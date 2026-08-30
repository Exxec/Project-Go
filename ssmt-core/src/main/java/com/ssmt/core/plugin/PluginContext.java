package com.ssmt.core.plugin;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Minimal filesystem context provided to a plugin during initialization.
 *
 * @param pluginDirectory plugin installation directory
 * @param workingDirectory writable directory for this toolkit run
 */
public record PluginContext(Path pluginDirectory, Path workingDirectory) {

    public PluginContext {
        Objects.requireNonNull(pluginDirectory, "pluginDirectory must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
    }
}
