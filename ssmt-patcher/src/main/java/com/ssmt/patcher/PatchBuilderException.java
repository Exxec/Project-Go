package com.ssmt.patcher;

import com.ssmt.core.exception.SsmtException;

/**
 * Reports patch assembly failures.
 */
public final class PatchBuilderException extends SsmtException {
    private static final long serialVersionUID = 1L;

    PatchBuilderException(String message) {
        super(message);
    }

    PatchBuilderException(String message, Throwable cause) {
        super(message, cause);
    }
}
