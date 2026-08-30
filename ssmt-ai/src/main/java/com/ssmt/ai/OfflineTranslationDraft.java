package com.ssmt.ai;

import java.util.Objects;
import java.util.List;

/** Translation plus provenance and review state from the offline provider chain. */
public record OfflineTranslationDraft(
        AiTranslationRequest request,
        String translatedText,
        OfflineTranslationOrigin origin,
        boolean requiresReview,
        String fallbackReason,
        TranslationAssessment assessment,
        List<OfflineTranslationAttempt> attempts) {

    public OfflineTranslationDraft {
        request = Objects.requireNonNull(request, "request");
        if (translatedText == null || translatedText.isBlank()) {
            throw new IllegalArgumentException("translatedText must not be blank");
        }
        origin = Objects.requireNonNull(origin, "origin");
        fallbackReason = Objects.requireNonNullElse(fallbackReason, "");
        assessment = Objects.requireNonNull(assessment, "assessment");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
    }
}
