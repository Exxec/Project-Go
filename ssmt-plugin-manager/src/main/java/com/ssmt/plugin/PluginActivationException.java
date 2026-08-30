package com.ssmt.plugin;

import com.ssmt.core.exception.SsmtException;

/**
 * Reports isolated plugin worker failures.
 */
public final class PluginActivationException extends SsmtException {
    private static final long serialVersionUID = 1L;

    PluginActivationException(String message) {
        super(message);
    }

    PluginActivationException(String message, Throwable cause) {
        super(message, cause);
    }
}
