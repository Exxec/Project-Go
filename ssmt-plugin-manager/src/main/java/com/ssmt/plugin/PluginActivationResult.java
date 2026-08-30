package com.ssmt.plugin;

/**
 * Verified result returned by an isolated plugin worker.
 *
 * @param id runtime plugin id
 * @param version runtime plugin version
 */
public record PluginActivationResult(String id, String version) {
    /**
     * Validates worker identity.
     */
    public PluginActivationResult {
        if (id == null || id.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Plugin result identity must not be blank");
        }
    }
}
