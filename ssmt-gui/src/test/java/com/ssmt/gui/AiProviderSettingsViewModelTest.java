package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.ai.AiProviderType;
import org.junit.jupiter.api.Test;

class AiProviderSettingsViewModelTest {
    @Test
    void retainsEnvironmentReferenceWithoutCredentialValue() {
        AiProviderSettingsViewModel model = new AiProviderSettingsViewModel();

        model.update(
                AiProviderType.OPENAI,
                "https://api.openai.com",
                "gpt-test",
                "SSMT_OPENAI_KEY");

        assertThat(model.settings().orElseThrow().credentialEnvironmentVariable())
                .contains("SSMT_OPENAI_KEY");
        assertThat(model.settings().orElseThrow().toString()).doesNotContain("secret");
        assertThatThrownBy(() -> model.update(
                        AiProviderType.OPENAI,
                        "https://api.openai.com",
                        "gpt-test",
                        "bad-name"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
