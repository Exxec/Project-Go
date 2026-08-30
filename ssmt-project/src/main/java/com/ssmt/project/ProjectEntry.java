package com.ssmt.project;

import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;

/**
 * One stable-key translation in a portable project.
 *
 * @param sourceFile normalized relative source path
 * @param key extractor stable key
 * @param originalText exact extracted source text
 * @param translatedText editable target text
 * @param provenance translation origin
 */
public record ProjectEntry(
        Path sourceFile,
        String key,
        String originalText,
        String translatedText,
        TranslationProvenance provenance) {

    /**
     * Backward-compatible constructor for legacy/manual project entries.
     */
    public ProjectEntry(
            Path sourceFile,
            String key,
            String originalText,
            String translatedText) {
        this(sourceFile, key, originalText, translatedText,
                TranslationProvenance.MANUAL_IMPORT);
    }

    public ProjectEntry {
        if (sourceFile == null
                || sourceFile.isAbsolute()
                || sourceFile.normalize().startsWith("..")) {
            throw new IllegalArgumentException("sourceFile must be a safe relative path");
        }
        sourceFile = sourceFile.normalize();
        if (key == null || key.isBlank() || originalText == null
                || translatedText == null || provenance == null) {
            throw new IllegalArgumentException("Project entry values must not be null or blank");
        }
    }

    /**
     * Returns a copy with a new draft.
     *
     * @param value translated text
     * @return updated immutable entry
     */
    public ProjectEntry withTranslatedText(String value) {
        return withTranslation(value, TranslationProvenance.HUMAN_EDITED);
    }

    /**
     * Returns a copy with text and explicit provenance.
     *
     * @param value translated text
     * @param valueProvenance translation origin
     * @return updated immutable entry
     */
    public ProjectEntry withTranslation(
            String value,
            TranslationProvenance valueProvenance) {
        return new ProjectEntry(
                sourceFile, key, originalText, value, valueProvenance);
    }
}
