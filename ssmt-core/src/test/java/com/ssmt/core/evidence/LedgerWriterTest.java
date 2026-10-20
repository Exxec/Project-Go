package com.ssmt.core.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerWriterTest {
    @TempDir Path directory;

    @Test void structuralCountsNeverEstablishRuntimePass() {
        var candidate = new RuntimeEvidence.Candidate("mod", "candidate", "a".repeat(64));
        assertThat(RuntimeEvidence.of(candidate, 10, 2, 0).verdict()).isEqualTo("NOT_TESTED");
        assertThatThrownBy(() -> SchemaValidator.validateLedger(new RuntimeEvidence.Ledger(
                candidate, 10, 2, 0, "PASS", "not observed")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void writesEscapedJsonAndPreservesExistingEvidence() throws Exception {
        Path path = directory.resolve("ledger.json");
        var candidate = new RuntimeEvidence.Candidate("m\"\\\n", "candidate", "a".repeat(64));
        var ledger = RuntimeEvidence.of(candidate, 10, 2, 0);
        LedgerWriter.writeLedger(path.toString(), ledger);
        String original = Files.readString(path);
        assertThat(original).startsWith("{\"candidate\":{\"mod_id\":\"m\\\"\\\\\\u000a\"")
                .endsWith("}\n");
        assertThatThrownBy(() -> LedgerWriter.writeLedger(path.toString(), ledger))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(path)).isEqualTo(original);
    }

    @Test void rejectsInvalidHashBeforeCreatingEvidence() {
        var ledger = new RuntimeEvidence.Ledger(
                new RuntimeEvidence.Candidate("mod", "candidate", "z".repeat(64)),
                10, 2, 0, "NOT_TESTED", "not observed");
        Path path = directory.resolve("ledger.json");
        assertThatThrownBy(() -> LedgerWriter.writeLedger(path.toString(), ledger))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(path).doesNotExist();
    }
}
