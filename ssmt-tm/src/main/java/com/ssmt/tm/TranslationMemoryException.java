package com.ssmt.tm;

import com.ssmt.core.exception.SsmtException;

/**
 * Reports translation-memory persistence and identity failures.
 */
public final class TranslationMemoryException extends SsmtException {
    private static final long serialVersionUID = 1L;

    TranslationMemoryException(String message) {
        super(message);
    }

    TranslationMemoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
