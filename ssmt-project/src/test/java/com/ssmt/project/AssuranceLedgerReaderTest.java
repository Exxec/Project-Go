package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssuranceLedgerReaderTest {
    private static final String HASH = "a".repeat(64);
    @TempDir Path root;

    private Path ledger(String evidencePath) throws Exception {
        Path evidence = root.resolve("result.log");
        Path input = Files.writeString(root.resolve("source.zip"), "source input");
        Path output = Files.writeString(root.resolve("output.jar"), "compiled output");
        var verified = BuildEvidenceReader.AuthorityDisposition.VERIFIED;
        var profile = new BuildEvidenceReader.Profile(1, HASH, "jdk/bin/java", "25",
                List.of("gradlew.bat", "build", "--offline"), ".", 0,
                List.of(reference(input)), List.of(), List.of(reference(output)),
                List.of(new BuildEvidenceReader.Authority("SOURCE", verified, "reviewed"),
                        new BuildEvidenceReader.Authority("COMPILED_JAR", verified, "recorded output"),
                        new BuildEvidenceReader.Authority("LOADER_PROVIDER", verified, "reviewed")));
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(evidence.toFile(), profile);
        String evidenceHash = com.ssmt.scanner.InventoryFingerprint.archive(evidence);
        Path runtimeLog = Files.writeString(root.resolve("runtime.log"), "runtime scenarios observed");
        Path runtimeEvidence = root.resolve("runtime.json");
        var scenarios = java.util.Arrays.stream(AssuranceSummary.Gate.values())
                .filter(AssuranceLedgerReaderTest::runtimeGate)
                .map(gate -> new RuntimeEvidenceReader.ScenarioResult(gate,
                        AssuranceSummary.Disposition.PASS, gate.name(), ""))
                .toList();
        var runtime = new RuntimeEvidenceReader.Profile(2, HASH, "0.98a-RC8",
                List.of(new RuntimeEvidenceReader.EnabledMod("candidate", "1.0")),
                List.of("candidate"), "java.exe", "25", 0,
                List.of(new RuntimeEvidenceReader.LogReference("runtime.log",
                        com.ssmt.scanner.InventoryFingerprint.archive(runtimeLog))),
                List.of(), scenarios);
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(runtimeEvidence.toFile(), runtime);
        var results = java.util.Arrays.stream(AssuranceSummary.Gate.values())
                .map(gate -> new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.PASS,
                        HASH, gate.name(), runtimeGate(gate) ? "runtime.json" : evidencePath, ""))
                .toList();
        var ledger = new AssuranceLedgerReader.Ledger(1, HASH, results,
                List.of(new AssuranceLedgerReader.Reference(evidencePath, evidenceHash),
                        new AssuranceLedgerReader.Reference("runtime.json",
                                com.ssmt.scanner.InventoryFingerprint.archive(runtimeEvidence))));
        Path file = root.resolve("ledger.json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(file.toFile(), ledger);
        return file;
    }

    private BuildEvidenceReader.FileReference reference(Path file) throws Exception {
        return new BuildEvidenceReader.FileReference(root.relativize(file).toString().replace('\\', '/'),
                com.ssmt.scanner.InventoryFingerprint.archive(file));
    }

    private static boolean runtimeGate(AssuranceSummary.Gate gate) {
        return switch (gate) {
            case AUTOMATED_BOOT, CAMPAIGN, COMBAT, SAVE_RELOAD, UPGRADE_COMPATIBILITY -> true;
            default -> false;
        };
    }

    @Test void verifiesReferencedBytesAndCandidateWithoutMutatingLedger() throws Exception {
        Path file = ledger("result.log");
        byte[] before = Files.readAllBytes(file);
        assertThat(new AssuranceLedgerReader().read(file, HASH).status()).isEqualTo("READY");
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        Files.writeString(root.resolve("result.log"), "tampered");
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH))
                .hasMessageContaining("hash mismatch");
    }

    @Test void rejectsEscapingReferencesAndForeignCandidate() throws Exception {
        Path file = ledger("../outside.log");
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH)).hasMessageContaining("escapes");
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, "b".repeat(64)))
                .hasMessageContaining("different candidate");
    }

    @Test void rejectsDuplicateUnknownAndTrailingCompletionDeclarations() throws Exception {
        Path file = ledger("result.log");
        String original = Files.readString(file);
        Files.writeString(file, original.substring(0, original.length() - 1) + ",\"status\":\"READY\"}");
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH)).isInstanceOf(java.io.IOException.class);
        Files.writeString(file, original.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"));
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH)).isInstanceOf(java.io.IOException.class);
        Files.writeString(file, original + " {}");
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH)).isInstanceOf(java.io.IOException.class);
    }

    @Test void rejectsMissingCandidateHashAndOversizedInput() throws Exception {
        Path file = ledger("result.log");
        String original = Files.readString(file);
        Files.writeString(file, original.replace("\"candidateSha256\":\"" + HASH + "\"",
                "\"candidateSha256\":null"));
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH)).isInstanceOf(java.io.IOException.class);
        Files.writeString(file, " ".repeat(1024 * 1024 + 1));
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH)).hasMessageContaining("exceeds 1 MiB");
    }

    @Test void rejectsGenericLogAsCompilationEvidence() throws Exception {
        Path evidence = Files.writeString(root.resolve("generic.log"), "compile succeeded");
        String evidenceHash = com.ssmt.scanner.InventoryFingerprint.archive(evidence);
        var results = java.util.Arrays.stream(AssuranceSummary.Gate.values())
                .map(gate -> new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.PASS,
                        HASH, gate.name(), "generic.log", "")).toList();
        var ledger = new AssuranceLedgerReader.Ledger(1, HASH, results,
                List.of(new AssuranceLedgerReader.Reference("generic.log", evidenceHash)));
        Path file = root.resolve("generic-ledger.json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(file.toFile(), ledger);

        assertThatThrownBy(() -> new AssuranceLedgerReader().read(file, HASH))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test void rejectsReadyWhenBuildFailedOrAuthorityRemainsUnresolved() throws Exception {
        Path ledgerFile = ledger("result.log");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Path buildFile = root.resolve("result.log");
        var valid = mapper.readValue(buildFile.toFile(), BuildEvidenceReader.Profile.class);
        var failed = new BuildEvidenceReader.Profile(valid.schemaVersion(), valid.candidateSha256(),
                valid.jdkExecutable(), valid.jdkVersion(), valid.command(), valid.workingDirectory(),
                7, valid.buildInputs(), valid.classpath(), valid.outputs(), valid.authorities());
        rewriteBuildAndReference(mapper, ledgerFile, buildFile, failed);
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(ledgerFile, HASH))
                .hasMessageContaining("exit code 0");

        var authorities = new ArrayList<>(valid.authorities());
        authorities.set(0, new BuildEvidenceReader.Authority("SOURCE",
                BuildEvidenceReader.AuthorityDisposition.REVIEW_REQUIRED, "review pending"));
        var unresolved = new BuildEvidenceReader.Profile(valid.schemaVersion(),
                valid.candidateSha256(), valid.jdkExecutable(), valid.jdkVersion(),
                valid.command(), valid.workingDirectory(), 0, valid.buildInputs(),
                valid.classpath(), valid.outputs(), authorities);
        rewriteBuildAndReference(mapper, ledgerFile, buildFile, unresolved);
        assertThatThrownBy(() -> new AssuranceLedgerReader().read(ledgerFile, HASH))
                .hasMessageContaining("VERIFIED authority: SOURCE");
    }

    @Test void rejectsBuildRecordReusedAsRuntimeEvidence() throws Exception {
        Path ledgerFile = ledger("result.log");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var ledger = mapper.readValue(ledgerFile.toFile(), AssuranceLedgerReader.Ledger.class);
        var results = ledger.results().stream().map(result -> runtimeGate(result.gate())
                ? new AssuranceSummary.Result(result.gate(), result.disposition(),
                        result.candidateSha256(), result.scenario(), "result.log", result.reason())
                : result).toList();
        var rewritten = new AssuranceLedgerReader.Ledger(1, HASH, results,
                List.of(new AssuranceLedgerReader.Reference("result.log",
                        com.ssmt.scanner.InventoryFingerprint.archive(root.resolve("result.log")))));
        mapper.writeValue(ledgerFile.toFile(), rewritten);

        assertThatThrownBy(() -> new AssuranceLedgerReader().read(ledgerFile, HASH))
                .isInstanceOf(java.io.IOException.class);
    }

    private static void rewriteBuildAndReference(
            com.fasterxml.jackson.databind.ObjectMapper mapper, Path ledgerFile, Path buildFile,
            BuildEvidenceReader.Profile profile) throws Exception {
        mapper.writeValue(buildFile.toFile(), profile);
        var ledger = mapper.readValue(ledgerFile.toFile(), AssuranceLedgerReader.Ledger.class);
        String buildHash = com.ssmt.scanner.InventoryFingerprint.archive(buildFile);
        var references = ledger.references().stream().map(reference -> reference.path().equals("result.log")
                ? new AssuranceLedgerReader.Reference(reference.path(), buildHash)
                : reference).toList();
        mapper.writeValue(ledgerFile.toFile(), new AssuranceLedgerReader.Ledger(
                ledger.schemaVersion(), ledger.candidateSha256(), ledger.results(), references));
    }
}
