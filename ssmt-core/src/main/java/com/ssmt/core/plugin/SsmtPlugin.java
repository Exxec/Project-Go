package com.ssmt.core.plugin;

import com.ssmt.core.exception.PluginLoadException;

/**
 * Stable base lifecycle contract for SSMT plugins.
 */
public interface SsmtPlugin {

    /**
     * Initializes this plugin once.
     *
     * @param context restricted runtime context
     * @throws PluginLoadException when initialization fails
     */
    void initialize(PluginContext context) throws PluginLoadException;

    /**
     * @return globally unique plugin identifier
     */
    String getPluginId();

    /**
     * @return plugin semantic version
     */
    String getVersion();
}
