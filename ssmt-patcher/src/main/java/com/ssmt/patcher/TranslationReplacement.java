package com.ssmt.patcher;

import java.nio.file.Path;

/**
 * One verified source-to-target replacement.
 *
 * @param sourceFile relative source file
 * @param key extractor stable key
 * @param originalText expected current text
 * @param translatedText replacement text
 */
public record TranslationReplacement(
        Path sourceFile,
        String key,
        String originalText,
        String translatedText) {

    /**
     * Validates replacement identity.
     */
    public TranslationReplacement {
        if (sourceFile == null || sourceFile.isAbsolute() || sourceFile.normalize().startsWith("..")) {
            throw new IllegalArgumentException("sourceFile must be a safe relative path");
        }
        sourceFile = sourceFile.normalize();
        if (key == null || key.isBlank() || originalText == null || translatedText == null) {
            throw new IllegalArgumentException("Replacement values must not be null or blank");
        }
    }
}
