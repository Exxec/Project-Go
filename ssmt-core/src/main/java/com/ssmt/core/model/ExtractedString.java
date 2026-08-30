package com.ssmt.core.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Exact localizable text extracted from a source file.
 *
 * @param modId owning mod identifier
 * @param sourceFile path relative to the mod root
 * @param key stable identifier within the source file
 * @param originalText exact, unnormalized source text
 * @param lineNumber one-based source line, or {@code -1} when unavailable
 */
public record ExtractedString(
        String modId,
        Path sourceFile,
        String key,
        String originalText,
        int lineNumber
) {

    public ExtractedString {
        Objects.requireNonNull(modId, "modId must not be null");
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(originalText, "originalText must not be null");
        if (sourceFile.isAbsolute()) {
            throw new IllegalArgumentException("sourceFile must be relative to the mod root");
        }
        if (lineNumber == 0 || lineNumber < -1) {
            throw new IllegalArgumentException("lineNumber must be positive or -1");
        }
    }
}
