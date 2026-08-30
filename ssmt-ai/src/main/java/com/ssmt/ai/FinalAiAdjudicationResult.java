package com.ssmt.ai;

import java.util.Objects;

/** Unapproved result of the final adjudication stage. */
public record FinalAiAdjudicationResult(
        String translatedText,
        FinalAiAdjudicationStatus status,
        boolean requiresReview,
        AiRoutingAssessment routing,
        String detail) {
    public FinalAiAdjudicationResult {
        if (translatedText == null || translatedText.isBlank()) {
            throw new IllegalArgumentException("translatedText must not be blank");
        }
        status = Objects.requireNonNull(status, "status");
        routing = Objects.requireNonNull(routing, "routing");
        detail = Objects.requireNonNullElse(detail, "");
    }
}
