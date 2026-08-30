package com.ssmt.plugin;

import com.ssmt.core.exception.SsmtException;

/**
 * Reports invalid or incompatible plugin archives.
 */
public final class PluginCatalogException extends SsmtException {
    private static final long serialVersionUID = 1L;

    PluginCatalogException(String message) {
        super(message);
    }

    PluginCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
