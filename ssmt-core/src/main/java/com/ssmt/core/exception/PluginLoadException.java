package com.ssmt.core.exception;

/**
 * Indicates that a plugin could not initialize.
 */
public final class PluginLoadException extends SsmtException {

    private static final long serialVersionUID = 1L;

    public PluginLoadException(String message) {
        super(message);
    }

    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
