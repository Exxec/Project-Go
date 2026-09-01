package com.ssmt.tm;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the one per-user translation library shared by every SSMT entry point.
 */
public final class MasterTranslationLibrary {
    /** Default filename for the reusable SQLite translation library. */
    public static final String DEFAULT_FILENAME = "project-go-catalog.db";

    private MasterTranslationLibrary() {
    }

    /**
     * Resolves the current user's configured or default master library.
     * A system property takes priority over the environment variable so a
     * packaged or scripted invocation can choose a library without changing
     * the user's global environment.
     *
     * @return normalized master-library path
     */
    public static Path currentUserDefault() {
        String configured = System.getProperty("ssmt.catalog", "").strip();
        if (configured.isEmpty()) {
            configured = System.getenv().getOrDefault("SSMT_TRANSLATION_MEMORY", "").strip();
        }
        return resolve(
                configured.isEmpty() ? Optional.empty() : Optional.of(configured),
                Optional.ofNullable(System.getenv("LOCALAPPDATA")),
                Path.of(System.getProperty("user.home")));
    }

    /**
     * Resolves an explicit library path or the portable per-user default.
     *
     * @param configuredPath system-property or environment override
     * @param localAppData Windows local-app-data location when available
     * @param userHome fallback user-home directory
     * @return normalized master-library path
     */
    public static Path resolve(
            Optional<String> configuredPath, Optional<String> localAppData, Path userHome) {
        if (configuredPath.isPresent() && !configuredPath.orElseThrow().isBlank()) {
            return Path.of(configuredPath.orElseThrow()).toAbsolutePath().normalize();
        }
        if (localAppData.isPresent() && !localAppData.orElseThrow().isBlank()) {
            return Path.of(localAppData.orElseThrow(), "Project Go", DEFAULT_FILENAME)
                    .toAbsolutePath()
                    .normalize();
        }
        return userHome.resolve(".project-go").resolve(DEFAULT_FILENAME)
                .toAbsolutePath()
                .normalize();
    }
}
