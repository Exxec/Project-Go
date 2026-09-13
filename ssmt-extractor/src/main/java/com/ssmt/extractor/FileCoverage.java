package com.ssmt.extractor;

import java.nio.file.Path;
import java.util.Objects;

/** Observed file handling, not proof that every player-visible string was selected. */
public record FileCoverage(Path sourceFile, String handler, String status,
        int extractedStrings, String reason) {
    public FileCoverage {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        if (sourceFile.isAbsolute() || extractedStrings < 0) {
            throw new IllegalArgumentException("Coverage requires a relative path and nonnegative count");
        }
    }
}
