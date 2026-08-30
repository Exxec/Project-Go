package com.ssmt.core;

/**
 * Typed cooperative-cancellation signal.
 */
public final class OperationCancelledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OperationCancelledException(String message) {
        super(message);
    }
}
