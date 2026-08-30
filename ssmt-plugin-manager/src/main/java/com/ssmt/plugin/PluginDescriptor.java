package com.ssmt.plugin;

import java.nio.file.Path;

/**
 * Validated metadata for an uninitialized plugin archive.
 *
 * @param id globally unique id
 * @param name display name
 * @param version plugin version
 * @param apiVersion required SSMT plugin API version
 * @param providerClass declared provider class
 * @param archive normalized archive path
 */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        int apiVersion,
        String providerClass,
        Path archive) {

    /**
     * Validates catalog metadata.
     */
    public PluginDescriptor {
        requireText(id, "id");
        requireText(name, "name");
        requireText(version, "version");
        requireText(providerClass, "providerClass");
        if (apiVersion < 1) {
            throw new IllegalArgumentException("apiVersion must be positive");
        }
        if (archive == null || !archive.isAbsolute()) {
            throw new IllegalArgumentException("archive must be absolute");
        }
        archive = archive.normalize();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
