package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserDiagnosticTest {
    @Test
    void includesNestedOperatingSystemCauseAndHandlesMissingMessage() {
        var failure = new IllegalStateException("Could not unpack selected mod archive",
                new java.nio.file.AccessDeniedException("staging", "output", "File is busy"));
        assertThat(UserDiagnostic.failed("Open mod", failure).detail())
                .contains("AccessDeniedException", "File is busy", "staging", "output");
        assertThat(UserDiagnostic.failed("Open mod", new IllegalStateException()).detail())
                .contains("No additional technical detail");
        assertThat(UserDiagnostic.failed("Open mod", null).detail())
                .contains("No additional technical detail");
    }

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
