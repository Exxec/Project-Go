package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TranslationPromptBuilderTest {
    @Test
    void includesContextAndGlossaryDeterministically() {
        AiTranslationRequest request =
                new AiTranslationRequest("Flux", "en", "fr", "combat tooltip", "Flux=Flux");

        assertThat(new TranslationPromptBuilder().build(request))
                .isEqualTo("""
                        Translate the source text from en to fr.
                        Return only the translated text. Preserve placeholders and $tokens exactly.
                        Context:
                        combat tooltip
                        Glossary:
                        Flux=Flux
                        Source text:
                        Flux""");
    }
}
