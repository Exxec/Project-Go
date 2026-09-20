package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    @Test void rejectsFinalizationWhenPassingBuildExitedNonzero() throws Exception {
        Fixture fixture = fixture();
        var mapper = new ObjectMapper();
        var build = mapper.readValue(fixture.build().toFile(), BuildEvidenceReader.Profile.class);
        rewriteBuild(fixture, new BuildEvidenceReader.Profile(build.schemaVersion(),
                build.candidateSha256(), build.jdkExecutable(), build.jdkVersion(),
                build.command(), build.workingDirectory(), 2, build.buildInputs(),
                build.classpath(), build.outputs(), build.authorities()));

        assertThatThrownBy(() -> finalize(fixture)).hasMessageContaining("exit code 0");
    }

    @Test void rejectsFinalizationWhenPassingBuildAuthorityIsUnresolved() throws Exception {
        Fixture fixture = fixture();
        var mapper = new ObjectMapper();
        var build = mapper.readValue(fixture.build().toFile(), BuildEvidenceReader.Profile.class);
        var authorities = new ArrayList<>(build.authorities());
        authorities.set(0, new BuildEvidenceReader.Authority("SOURCE",
                BuildEvidenceReader.AuthorityDisposition.REVIEW_REQUIRED, "pending"));
        rewriteBuild(fixture, new BuildEvidenceReader.Profile(build.schemaVersion(),
                build.candidateSha256(), build.jdkExecutable(), build.jdkVersion(),
                build.command(), build.workingDirectory(), 0, build.buildInputs(),
                build.classpath(), build.outputs(), authorities));

        assertThatThrownBy(() -> finalize(fixture))
                .hasMessageContaining("VERIFIED authority: SOURCE");
    }

    @Test void rejectsFinalizationWhenBuildRecordIsReusedForRuntimeGates() throws Exception {
        Fixture fixture = fixture();
        var mapper = new ObjectMapper();
        var ledger = mapper.readValue(fixture.ledger().toFile(), AssuranceLedgerReader.Ledger.class);
        var results = ledger.results().stream().map(result -> runtimeGate(result.gate())
                ? new AssuranceSummary.Result(result.gate(), result.disposition(),
                        result.candidateSha256(), result.scenario(), "build.json", result.reason())
                : result).toList();
        var reference = new AssuranceLedgerReader.Reference("build.json",
                InventoryFingerprint.archive(fixture.build()));
        mapper.writeValue(fixture.ledger().toFile(), new AssuranceLedgerReader.Ledger(
                ledger.schemaVersion(), ledger.candidateSha256(), results, List.of(reference)));

        assertThatThrownBy(() -> finalize(fixture)).isInstanceOf(java.io.IOException.class);
    }

    private static FinalEvidenceService.Report finalize(Fixture fixture) throws Exception {
        return new FinalEvidenceService().finalizeEvidence(fixture.candidate(), fixture.archive(),
                "wrapper", fixture.ledger(), fixture.feedback());
    }

    private static void rewriteBuild(Fixture fixture, BuildEvidenceReader.Profile profile)
            throws Exception {
        var mapper = new ObjectMapper();
        mapper.writeValue(fixture.build().toFile(), profile);
        var ledger = mapper.readValue(fixture.ledger().toFile(), AssuranceLedgerReader.Ledger.class);
        String hash = InventoryFingerprint.archive(fixture.build());
        var references = ledger.references().stream().map(reference ->
                reference.path().equals("build.json")
                        ? new AssuranceLedgerReader.Reference(reference.path(), hash)
                        : reference).toList();
        mapper.writeValue(fixture.ledger().toFile(), new AssuranceLedgerReader.Ledger(
                ledger.schemaVersion(), ledger.candidateSha256(), ledger.results(), references));
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
        Path runtimeLog = Files.writeString(evidenceRoot.resolve("runtime.log"), "all scenarios observed");
        var runtimeScenarios = Arrays.stream(AssuranceSummary.Gate.values())
                .filter(FinalEvidenceServiceTest::runtimeGate)
                .map(gate -> new RuntimeEvidenceReader.ScenarioResult(gate,
                        AssuranceSummary.Disposition.PASS, gate.name(), ""))
                .toList();
        var runtime = new RuntimeEvidenceReader.Profile(2, candidateHash, "0.98a-RC8",
                List.of(new RuntimeEvidenceReader.EnabledMod("candidate", "1.0")),
                List.of("candidate"), "java.exe", "25", 0,
                List.of(new RuntimeEvidenceReader.LogReference("runtime.log",
                        InventoryFingerprint.archive(runtimeLog))), List.of(), runtimeScenarios);
        Path runtimeFile = evidenceRoot.resolve("runtime.json");
        new ObjectMapper().writeValue(runtimeFile.toFile(), runtime);
        var results = Arrays.stream(AssuranceSummary.Gate.values()).map(gate ->
                new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.PASS,
                        candidateHash, gate.name(), runtimeGate(gate) ? "runtime.json" : "build.json", ""))
                .toList();
        var ledger = new AssuranceLedgerReader.Ledger(1, candidateHash, results,
                List.of(new AssuranceLedgerReader.Reference("build.json",
                                InventoryFingerprint.archive(buildFile)),
                        new AssuranceLedgerReader.Reference("runtime.json",
                                InventoryFingerprint.archive(runtimeFile))));
        Path ledgerFile = evidenceRoot.resolve("ledger.json");
        new ObjectMapper().writeValue(ledgerFile.toFile(), ledger);
        var feedback = new AttemptFeedbackReader.Record(
                1, candidateHash, "attempt-1", true, List.of(), List.of());
        Path feedbackFile = evidenceRoot.resolve("feedback.json");
        new ObjectMapper().writeValue(feedbackFile.toFile(), feedback);
        return new Fixture(candidate, archive, ledgerFile, feedbackFile, buildFile);
    }

    private static BuildEvidenceReader.FileReference reference(Path parent, Path file) throws Exception {
        return new BuildEvidenceReader.FileReference(
                parent.relativize(file).toString().replace('\\', '/'),
                InventoryFingerprint.archive(file));
    }

    private static boolean runtimeGate(AssuranceSummary.Gate gate) {
        return switch (gate) {
            case AUTOMATED_BOOT, CAMPAIGN, COMBAT, SAVE_RELOAD, UPGRADE_COMPATIBILITY -> true;
            default -> false;
        };
    }

    private record Fixture(Path candidate, Path archive, Path ledger, Path feedback, Path build) { }
}
