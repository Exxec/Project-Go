package com.ssmt.plugin;

/**
 * Requested plugin worker isolation strength.
 */
public enum PluginSandboxProfile {
    /**
     * Uses only the bounded worker JVM.
     */
    PROCESS_ONLY,

    /**
     * Uses a supported OS wrapper when available, otherwise the worker JVM.
     */
    AUTO,

    /**
     * Requires a supported OS wrapper and fails before launch otherwise.
     */
    REQUIRED
}
