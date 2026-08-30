package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiProviderSettingsTest {
    @Test
    void storesOnlyEnvironmentVariableReference() {
        AiProviderSettings settings = new AiProviderSettings(
                AiProviderType.OPENAI,
                URI.create("https://api.openai.com"),
                "gpt-test",
                Optional.of("SSMT_OPENAI_KEY"));

        assertThat(settings.toString()).contains("SSMT_OPENAI_KEY").doesNotContain("secret-value");
        assertThat(settings.create(name -> "secret-value"))
                .isInstanceOf(OpenAiTranslationProvider.class);
    }

    @Test
    void rejectsMissingCredentialAndInvalidVariableName() {
        assertThatThrownBy(() -> new AiProviderSettings(
                        AiProviderType.GEMINI,
                        URI.create("https://example.invalid"),
                        "gemini-test",
                        Optional.of("bad-name")))
                .isInstanceOf(IllegalArgumentException.class);
        AiProviderSettings settings = new AiProviderSettings(
                AiProviderType.OPENAI,
                URI.create("https://example.invalid"),
                "gpt-test",
                Optional.of("SSMT_KEY"));
        assertThatThrownBy(() -> settings.create(name -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
