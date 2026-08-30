package com.ssmt.tm;

import com.ssmt.core.model.TranslationProvenance;
import java.util.Objects;

/**
 * Values supplied when a translation-memory entry is created or replaced.
 *
 * @param sourceText exact source text
 * @param sourceLanguage source language identifier
 * @param targetLanguage target language identifier
 * @param translatedText translated text
 * @param context optional disambiguating context
 * @param provenance translation origin
 */
public record TranslationDraft(
        String sourceText,
        String sourceLanguage,
        String targetLanguage,
        String translatedText,
        String context,
        TranslationProvenance provenance) {

    /**
     * Backward-compatible constructor for legacy/manual imports.
     */
    public TranslationDraft(
            String sourceText,
            String sourceLanguage,
            String targetLanguage,
            String translatedText,
            String context) {
        this(sourceText, sourceLanguage, targetLanguage, translatedText,
                context, TranslationProvenance.MANUAL_IMPORT);
    }

    /**
     * Validates required values and normalizes a null context to the empty string.
     */
    public TranslationDraft {
        requireText(sourceText, "sourceText");
        requireText(sourceLanguage, "sourceLanguage");
        requireText(targetLanguage, "targetLanguage");
        requireText(translatedText, "translatedText");
        context = Objects.requireNonNullElse(context, "");
        provenance = Objects.requireNonNull(provenance, "provenance");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
