package com.ssmt.gui;

import java.nio.file.Path;

/**
 * Stable editor-row identity.
 *
 * @param sourceFile relative source file
 * @param key stable extractor key
 */
public record TranslationRowId(Path sourceFile, String key) {
    /**
     * Validates stable identity.
     */
    public TranslationRowId {
        if (sourceFile == null || sourceFile.isAbsolute() || sourceFile.normalize().startsWith("..")) {
            throw new IllegalArgumentException("sourceFile must be a safe relative path");
        }
        sourceFile = sourceFile.normalize();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
