package com.ssmt.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Hash-bound, read-only description of one documented legacy project. */
public record LegacyProjectCandidate(Path file, String sha256, long bytes,
        String sourceModId, String projectName, String patchId, int entries,
        int translatedEntries, Instant modifiedAt) {
    public LegacyProjectCandidate {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(sourceModId, "sourceModId");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(patchId, "patchId");
        Objects.requireNonNull(modifiedAt, "modifiedAt");
        if (bytes < 0 || entries < 0 || translatedEntries < 0
                || translatedEntries > entries) {
            throw new IllegalArgumentException("legacy project counts must be valid");
        }
    }
}
