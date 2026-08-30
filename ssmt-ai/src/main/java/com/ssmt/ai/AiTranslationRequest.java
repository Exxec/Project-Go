package com.ssmt.ai;

import java.util.Objects;

/**
 * Input for one optional draft translation.
 *
 * @param sourceText exact source text
 * @param sourceLanguage source language
 * @param targetLanguage target language
 * @param context optional context
 * @param glossary optional glossary content
 * @param preparedPrompt optional complete provider prompt
 */
public record AiTranslationRequest(
        String sourceText,
        String sourceLanguage,
        String targetLanguage,
        String context,
        String glossary,
        String preparedPrompt) {

    public AiTranslationRequest(
            String sourceText,
            String sourceLanguage,
            String targetLanguage,
            String context,
            String glossary) {
        this(sourceText, sourceLanguage, targetLanguage, context, glossary, "");
    }

    /**
     * Validates required input and normalizes optional text.
     */
    public AiTranslationRequest {
        requireText(sourceText, "sourceText");
        requireText(sourceLanguage, "sourceLanguage");
        requireText(targetLanguage, "targetLanguage");
        context = Objects.requireNonNullElse(context, "");
        glossary = Objects.requireNonNullElse(glossary, "");
        preparedPrompt = Objects.requireNonNullElse(preparedPrompt, "");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
