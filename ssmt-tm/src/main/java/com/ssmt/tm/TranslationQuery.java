package com.ssmt.tm;

import java.util.Objects;

/**
 * Parameters for deterministic translation-memory matching.
 *
 * @param sourceText text to match
 * @param sourceLanguage required source language
 * @param targetLanguage required target language
 * @param context required context, or empty string
 * @param minimumScore inclusive similarity threshold from zero to one
 * @param limit maximum number of matches
 */
public record TranslationQuery(
        String sourceText,
        String sourceLanguage,
        String targetLanguage,
        String context,
        double minimumScore,
        int limit) {

    /**
     * Validates matching parameters and normalizes null context.
     */
    public TranslationQuery {
        requireText(sourceText, "sourceText");
        requireText(sourceLanguage, "sourceLanguage");
        requireText(targetLanguage, "targetLanguage");
        context = Objects.requireNonNullElse(context, "");
        if (!Double.isFinite(minimumScore) || minimumScore < 0.0 || minimumScore > 1.0) {
            throw new IllegalArgumentException("minimumScore must be between 0 and 1");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
