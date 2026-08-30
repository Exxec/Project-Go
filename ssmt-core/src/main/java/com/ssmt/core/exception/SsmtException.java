package com.ssmt.core.exception;

/**
 * Base checked exception for domain failures reported by SSMT.
 */
public abstract class SsmtException extends Exception {

    private static final long serialVersionUID = 1L;

    protected SsmtException(String message) {
        super(message);
    }

    protected SsmtException(String message, Throwable cause) {
        super(message, cause);
    }
}
