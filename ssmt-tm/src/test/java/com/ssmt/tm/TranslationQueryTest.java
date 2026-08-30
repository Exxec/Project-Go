package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TranslationQueryTest {
    @Test
    void rejectsInvalidThresholdAndLimit() {
        assertThatThrownBy(() -> new TranslationQuery("Hello", "en", "fr", "", -0.1, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranslationQuery("Hello", "en", "fr", "", 1.1, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranslationQuery("Hello", "en", "fr", "", 0.8, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
