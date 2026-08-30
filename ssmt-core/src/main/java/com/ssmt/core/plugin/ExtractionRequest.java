package com.ssmt.core.plugin;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Read-only input boundary for a single source-file extraction.
 *
 * @param modId owning mod identifier
 * @param modRoot absolute mod root
 * @param sourceFile absolute source file contained by {@code modRoot}
 */
public record ExtractionRequest(String modId, Path modRoot, Path sourceFile) {

    public ExtractionRequest {
        Objects.requireNonNull(modId, "modId must not be null");
        if (modId.isBlank()) {
            throw new IllegalArgumentException("modId must not be blank");
        }
        modRoot = Objects.requireNonNull(modRoot, "modRoot must not be null")
                .toAbsolutePath()
                .normalize();
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile must not be null")
                .toAbsolutePath()
                .normalize();
        if (!sourceFile.startsWith(modRoot) || sourceFile.equals(modRoot)) {
            throw new IllegalArgumentException("sourceFile must be inside modRoot");
        }
    }

    /**
     * @return normalized path relative to the owning mod root
     */
    public Path relativeSourceFile() {
        return modRoot.relativize(sourceFile);
    }
}
