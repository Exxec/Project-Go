package com.ssmt.tm;

import java.time.Instant;
import java.util.Objects;

/** Provider/model lineage stored alongside a translation-memory entry. */
public record TranslationGenerationMetadata(
        String providerId,
        String modelOrLanguagePackage,
        String providerVersion,
        Instant generatedAt,
        boolean aiRefined,
        TranslationReviewStatus reviewStatus) {

    public TranslationGenerationMetadata {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        modelOrLanguagePackage = Objects.requireNonNullElse(modelOrLanguagePackage, "");
        providerVersion = Objects.requireNonNullElse(providerVersion, "");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        reviewStatus = Objects.requireNonNull(reviewStatus, "reviewStatus");
    }
}
