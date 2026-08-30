package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserDiagnosticTest {
    @Test
    void explainsFailureSafetyAndNextAction() {
        UserDiagnostic diagnostic = UserDiagnostic.failed(
                "Translate project", new IllegalStateException("Provider unavailable"));

        assertThat(diagnostic.summary()).isEqualTo("Translate project did not complete");
        assertThat(diagnostic.detail())
                .contains("Provider unavailable")
                .contains("No source-mod files were changed")
                .contains("try again")
                .contains("export diagnostics");
    }
}
