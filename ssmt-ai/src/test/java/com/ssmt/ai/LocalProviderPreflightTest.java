package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalProviderPreflightTest {
    private final LocalProviderPreflight preflight = new LocalProviderPreflight();

    @Test
    void reportsConfigurationWithoutRunningOrDownloadingProviders() {
        var findings = preflight.inspect(
                Path.of("argos-translate"),
                Path.of("missing", "translateLocally.exe"),
                "Helsinki-NLP/opus-mt-zh-en");

        assertThat(findings).containsExactly(
                "Argos Translate executable configured as argos-translate (runtime initialization not attempted).",
                "TranslateLocally executable was not found at "
                        + Path.of("missing", "translateLocally.exe") + ".",
                "TranslateLocally model configured: Helsinki-NLP/opus-mt-zh-en (installation not verified; no download attempted).");
    }

    @Test
    void explainsMissingConfigurationAndNeverSelectsAModel() {
        assertThat(preflight.inspect(null, null, ""))
                .containsExactly(
                        "Argos Translate executable is not configured.",
                        "TranslateLocally executable is not configured.",
                        "TranslateLocally model is not configured. Choose an installed model; SSMT will not download one automatically.");
    }
}
