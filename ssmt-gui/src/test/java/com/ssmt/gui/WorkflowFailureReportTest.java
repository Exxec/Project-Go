package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowFailureReportTest {
    @TempDir Path directory;

    @Test
    void createsAbsentProfileAndDistinctReportsWithFullFailureEvidence() throws Exception {
        Path storage = directory.resolve("fresh-profile/diagnostics");
        var failure = new IllegalStateException("Could not unpack archive",
                new java.nio.file.AccessDeniedException("staging", "output", "Busy"));
        Path first = WorkflowFailureReport.save(storage, List.of(), "Open mod", failure);
        String evidence = Files.readString(first);
        Path second = WorkflowFailureReport.save(storage, List.of(), "Open mod", failure);
        assertThat(second).isNotEqualTo(first);
        assertThat(evidence).contains("Operation: Open mod", "AccessDeniedException", "Busy", "Caused by");
        assertThat(Files.readString(first)).isEqualTo(evidence);
    }

    @Test
    void refusesSourceOverlapBeforeCreatingDiagnosticDirectory() throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path marker = Files.writeString(source.resolve("mod_info.json"), "keep");
        Path storage = source.resolve("profile/diagnostics");
        assertThatThrownBy(() -> WorkflowFailureReport.save(storage, List.of(source), "Open mod", null))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("outside the source mod");
        assertThat(storage.getParent()).doesNotExist();
        assertThat(marker).hasContent("keep");
    }

    @Test
    void failedReportWriteKeepsExistingFile() throws Exception {
        Path file = Files.writeString(directory.resolve("not-a-directory"), "keep");
        assertThatThrownBy(() -> WorkflowFailureReport.save(file.resolve("diagnostics"),
                List.of(), "Open mod", new IllegalStateException("Original failure")))
                .isInstanceOf(java.io.IOException.class);
        assertThat(file).hasContent("keep");
    }
}
