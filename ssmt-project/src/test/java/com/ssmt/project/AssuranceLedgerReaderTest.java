package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
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
        var results = java.util.Arrays.stream(AssuranceSummary.Gate.values())
                .map(gate -> new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.PASS,
                        HASH, gate.name(), evidencePath, "")).toList();
        var ledger = new AssuranceLedgerReader.Ledger(1, HASH, results,
                List.of(new AssuranceLedgerReader.Reference(evidencePath, evidenceHash)));
        Path file = root.resolve("ledger.json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(file.toFile(), ledger);
        return file;
    }

    private BuildEvidenceReader.FileReference reference(Path file) throws Exception {
        return new BuildEvidenceReader.FileReference(root.relativize(file).toString().replace('\\', '/'),
                com.ssmt.scanner.InventoryFingerprint.archive(file));
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
}
