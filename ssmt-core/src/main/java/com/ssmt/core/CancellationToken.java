package com.ssmt.core;

/**
 * Cooperative cancellation boundary for long-running work.
 */
@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    /**
     * @return whether cancellation has been requested
     */
    boolean isCancellationRequested();

    /**
     * Fails the current operation at a safe boundary when cancellation is requested.
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new OperationCancelledException("Operation cancelled");
        }
    }
}
