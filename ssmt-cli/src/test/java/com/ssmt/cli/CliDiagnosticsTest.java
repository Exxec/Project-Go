package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CliDiagnosticsTest {
    @Test
    void explainsOperationReasonAndNextAction() {
        String explanation = CliDiagnostics.explain(
                "Project build", new IllegalStateException("Provider unavailable"));

        assertThat(explanation)
                .startsWith("Project build failed:")
                .contains("Provider unavailable")
                .contains("check the inputs and try again");
    }

    @Test
    void fallsBackToGenericReasonWhenMessageIsBlank() {
        String explanation = CliDiagnostics.explain(
                "Project build", new IllegalStateException());

        assertThat(explanation).contains("No additional detail is available.");
    }
}
