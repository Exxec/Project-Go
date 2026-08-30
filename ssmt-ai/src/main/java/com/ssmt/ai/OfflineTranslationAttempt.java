package com.ssmt.ai;

import java.util.Objects;

/** One provider candidate retained for later comparison or adjudication. */
public record OfflineTranslationAttempt(
        OfflineTranslationOrigin origin,
        String translatedText,
        TranslationAssessment assessment,
        ProviderGenerationMetadata generationMetadata) {

    public OfflineTranslationAttempt(
            OfflineTranslationOrigin origin,
            String translatedText,
            TranslationAssessment assessment) {
        this(origin, translatedText, assessment, defaultMetadata(origin));
    }

    public OfflineTranslationAttempt {
        origin = Objects.requireNonNull(origin, "origin");
        if (translatedText == null || translatedText.isBlank()) {
            throw new IllegalArgumentException("translatedText must not be blank");
        }
        assessment = Objects.requireNonNull(assessment, "assessment");
        generationMetadata = Objects.requireNonNull(generationMetadata, "generationMetadata");
    }

    private static ProviderGenerationMetadata defaultMetadata(OfflineTranslationOrigin origin) {
        String provider = switch (origin) {
            case ARGOS_TRANSLATE -> "argos-translate";
            case TRANSLATE_LOCALLY -> "translate-locally";
            case APPROVED_GLOSSARY -> "approved-glossary";
        };
        return new ProviderGenerationMetadata(
                provider, "", "", java.time.Instant.now(), false);
    }
}
