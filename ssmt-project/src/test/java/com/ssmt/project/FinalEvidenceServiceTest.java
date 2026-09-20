package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinalEvidenceServiceTest {
    @TempDir Path root;

    @Test void bindsReadyRecordsToAnIdenticalFinalPackage() throws Exception {
        Fixture fixture = fixture();

        var report = new FinalEvidenceService().finalizeEvidence(fixture.candidate(),
                fixture.archive(), "wrapper", fixture.ledger(), fixture.feedback());

        assertThat(report.status()).isEqualTo("FINAL_BYTES_AND_RECORDED_EVIDENCE_AGREE");
        assertThat(report.evidenceSemanticsIndependentlyVerified()).isFalse();
        assertThat(report.packageSha256()).isEqualTo(InventoryFingerprint.archive(fixture.archive()));
    }

    @Test void rejectsPackageDriftAfterReportsAreFinal() throws Exception {
        Fixture fixture = fixture();
        try (var output = new ZipOutputStream(Files.newOutputStream(fixture.archive()))) {
            output.putNextEntry(new ZipEntry("wrapper/mod_info.json"));
            output.write("{\"id\":\"changed\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertThatThrownBy(() -> new FinalEvidenceService().finalizeEvidence(fixture.candidate(),
                fixture.archive(), "wrapper", fixture.ledger(), fixture.feedback()))
                .hasMessageContaining("bytes differ");
    }

    private Fixture fixture() throws Exception {
        Path candidate = Files.createDirectory(root.resolve("candidate"));
        Path metadata = Files.writeString(candidate.resolve("mod_info.json"), "{\"id\":\"candidate\"}");
        String candidateHash = InventoryFingerprint.tree(new CandidateInventory().capture(candidate).stream()
                .map(entry -> new InventoryFingerprint.File(entry.path(), entry.bytes(), entry.sha256())).toList());
        Path archive = root.resolve("candidate.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("wrapper/mod_info.json"));
            output.write(Files.readAllBytes(metadata));
            output.closeEntry();
        }
        Path evidenceRoot = Files.createDirectory(root.resolve("evidence"));
        Path input = Files.writeString(evidenceRoot.resolve("source.txt"), "source");
        Path built = Files.writeString(evidenceRoot.resolve("output.jar"), "output");
        var verified = BuildEvidenceReader.AuthorityDisposition.VERIFIED;
        var build = new BuildEvidenceReader.Profile(1, candidateHash, "jdk/bin/java", "25",
                List.of("gradlew.bat", "build"), ".", 0,
                List.of(reference(evidenceRoot, input)), List.of(), List.of(reference(evidenceRoot, built)),
                List.of(new BuildEvidenceReader.Authority("SOURCE", verified, "reviewed"),
                        new BuildEvidenceReader.Authority("COMPILED_JAR", verified, "recorded"),
                        new BuildEvidenceReader.Authority("LOADER_PROVIDER", verified, "reviewed")));
        Path buildFile = evidenceRoot.resolve("build.json");
        new ObjectMapper().writeValue(buildFile.toFile(), build);
        var results = Arrays.stream(AssuranceSummary.Gate.values()).map(gate ->
                new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.PASS,
                        candidateHash, gate.name(), "build.json", "")).toList();
        var ledger = new AssuranceLedgerReader.Ledger(1, candidateHash, results,
                List.of(new AssuranceLedgerReader.Reference("build.json",
                        InventoryFingerprint.archive(buildFile))));
        Path ledgerFile = evidenceRoot.resolve("ledger.json");
        new ObjectMapper().writeValue(ledgerFile.toFile(), ledger);
        var feedback = new AttemptFeedbackReader.Record(
                1, candidateHash, "attempt-1", true, List.of(), List.of());
        Path feedbackFile = evidenceRoot.resolve("feedback.json");
        new ObjectMapper().writeValue(feedbackFile.toFile(), feedback);
        return new Fixture(candidate, archive, ledgerFile, feedbackFile);
    }

    private static BuildEvidenceReader.FileReference reference(Path parent, Path file) throws Exception {
        return new BuildEvidenceReader.FileReference(
                parent.relativize(file).toString().replace('\\', '/'),
                InventoryFingerprint.archive(file));
    }

    private record Fixture(Path candidate, Path archive, Path ledger, Path feedback) { }
}
