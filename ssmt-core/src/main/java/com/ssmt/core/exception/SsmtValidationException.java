package com.ssmt.core.exception;

/**
 * Indicates that translated content failed a validation rule.
 */
public final class SsmtValidationException extends SsmtException {

    private static final long serialVersionUID = 1L;

    public SsmtValidationException(String message) {
        super(message);
    }

    public SsmtValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
