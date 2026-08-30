package com.ssmt.gui;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the persistent GUI translation catalog without consulting mod paths.
 */
public final class DefaultTranslationMemoryLocator {
    static final String DEFAULT_FILENAME = "ssmt-english-catalog.db";

    private DefaultTranslationMemoryLocator() {
    }

    /**
     * Prefers an explicitly remembered catalog, then the packaged executable
     * directory, then the process working directory.
     */
    public static Path resolve(
            Optional<String> rememberedPath,
            Optional<String> packagedApplicationPath,
            Path workingDirectory) {
        if (rememberedPath.isPresent() && !rememberedPath.orElseThrow().isBlank()) {
            return Path.of(rememberedPath.orElseThrow()).toAbsolutePath().normalize();
        }
        if (packagedApplicationPath.isPresent()
                && !packagedApplicationPath.orElseThrow().isBlank()) {
            Path executable = Path.of(packagedApplicationPath.orElseThrow())
                    .toAbsolutePath()
                    .normalize();
            Path parent = executable.getParent();
            if (parent != null) {
                return parent.resolve(DEFAULT_FILENAME);
            }
        }
        return workingDirectory.toAbsolutePath().normalize().resolve(DEFAULT_FILENAME);
    }

    /**
     * Resolves the same per-user catalog used by the headless application.
     */
    public static Path sharedHeadlessDefault(
            Optional<String> configuredPath,
            Optional<String> localAppData,
            Path userHome) {
        if (configuredPath.isPresent() && !configuredPath.orElseThrow().isBlank()) {
            return Path.of(configuredPath.orElseThrow()).toAbsolutePath().normalize();
        }
        if (localAppData.isPresent() && !localAppData.orElseThrow().isBlank()) {
            return Path.of(localAppData.orElseThrow(), "SSMT", DEFAULT_FILENAME)
                    .toAbsolutePath()
                    .normalize();
        }
        return userHome.resolve(".ssmt").resolve(DEFAULT_FILENAME)
                .toAbsolutePath()
                .normalize();
    }
}
