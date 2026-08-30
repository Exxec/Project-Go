package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TranslationDraftTest {
    @Test
    void rejectsBlankRequiredValues() {
        assertThatThrownBy(() -> new TranslationDraft("", "en", "fr", "Bonjour", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranslationDraft("Hello", " ", "fr", "Bonjour", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranslationDraft("Hello", "en", "fr", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
