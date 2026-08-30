package com.ssmt.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RuntimeBudgetsTest {
    @Test
    void defaultsAreFiniteAndCancellationIsTyped() {
        assertThat(RuntimeBudgets.DEFAULTS.maximumSourceFiles()).isPositive();
        assertThat(RuntimeBudgets.DEFAULTS.operationTimeout()).isLessThan(Duration.ofHours(1));
        assertThatThrownBy(((CancellationToken) () -> true)::throwIfCancellationRequested)
                .isInstanceOf(OperationCancelledException.class);
        CancellationToken.NONE.throwIfCancellationRequested();
    }

    @Test
    void rejectsUnboundedValues() {
        assertThatThrownBy(() -> new RuntimeBudgets(
                        0, 1, Duration.ofSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
