package com.ssmt.ai;

import java.util.List;
import java.util.Objects;

/** Confidence category and stable human-readable reasons. */
public record TranslationAssessment(
        TranslationConfidence confidence,
        List<String> reasons) {

    public TranslationAssessment {
        confidence = Objects.requireNonNull(confidence, "confidence");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (confidence != TranslationConfidence.HIGH && reasons.isEmpty()) {
            throw new IllegalArgumentException("non-high assessment requires a reason");
        }
    }

    public static TranslationAssessment high() {
        return new TranslationAssessment(TranslationConfidence.HIGH, List.of());
    }
}
