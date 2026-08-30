package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TranslationResourceLimitsTest {
    @Test
    void providesConservativeDefaults() {
        assertThat(TranslationResourceLimits.defaults())
                .isEqualTo(new TranslationResourceLimits(1, 32, OptionalLong.empty()));
    }

    @Test
    void rejectsNonPositiveLimitsAndGpuBudgets() {
        assertThatThrownBy(() -> new TranslationResourceLimits(0, 1, OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranslationResourceLimits(1, 0, OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranslationResourceLimits(1, 1, OptionalLong.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
