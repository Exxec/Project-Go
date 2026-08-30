package com.ssmt.tm;

import com.ssmt.core.model.TranslationProvenance;
import java.time.Instant;

/**
 * A persisted translation-memory entry.
 *
 * @param id database identity
 * @param sourceText exact source text
 * @param sourceLanguage source language identifier
 * @param targetLanguage target language identifier
 * @param translatedText translated text
 * @param context disambiguating context
 * @param provenance translation origin
 * @param createdAt creation timestamp
 * @param updatedAt most recent update timestamp
 */
public record TranslationEntry(
        long id,
        String sourceText,
        String sourceLanguage,
        String targetLanguage,
        String translatedText,
        String context,
        TranslationProvenance provenance,
        Instant createdAt,
        Instant updatedAt) {
}
