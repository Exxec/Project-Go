package com.ssmt.core;

import java.time.Duration;

/**
 * Explicit bounded-work defaults for long-running offline operations.
 *
 * @param maximumSourceFiles maximum files accepted in one source operation
 * @param maximumInputBytes maximum bytes accepted for one parsed file
 * @param operationTimeout default long-operation timeout
 * @param diagnosticEntryLimit maximum findings in one diagnostic export
 */
public record RuntimeBudgets(
        int maximumSourceFiles,
        long maximumInputBytes,
        Duration operationTimeout,
        int diagnosticEntryLimit) {
    public static final RuntimeBudgets DEFAULTS = new RuntimeBudgets(
            250_000,
            256L * 1024L * 1024L,
            Duration.ofMinutes(30),
            10_000);

    public RuntimeBudgets {
        if (maximumSourceFiles < 1
                || maximumInputBytes < 1
                || operationTimeout == null
                || operationTimeout.isNegative()
                || operationTimeout.isZero()
                || diagnosticEntryLimit < 1) {
            throw new IllegalArgumentException("Runtime budgets must be positive");
        }
    }
}
