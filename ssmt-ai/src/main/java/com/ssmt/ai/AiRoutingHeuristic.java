package com.ssmt.ai;

import java.util.ArrayList;
import java.util.List;

/** Computes an explainable routing heuristic from observable translation signals. */
public final class AiRoutingHeuristic {
    private static final int LONG_SOURCE_LENGTH = 120;

    public AiRoutingAssessment assess(OfflineTranslationDraft draft) {
        List<String> reasons = new ArrayList<>();
        int score = 0;
        if (draft.assessment().confidence() == TranslationConfidence.UNSAFE) {
            score += 3;
            reasons.add("protected syntax or validation failure +3");
        }
        if (contains(draft, "unchanged from source")) {
            score += 3;
            reasons.add("source language likely remains +3");
        }
        if (draft.request().sourceText().length() > LONG_SOURCE_LENGTH) {
            score += 2;
            reasons.add("long descriptive source +2");
        }
        if (contains(draft, "candidates disagree")) {
            score += 2;
            reasons.add("independent local candidates disagree +2");
        }
        if (contains(draft, "length is implausible")) {
            score += 1;
            reasons.add("large length deviation +1");
        }
        AiRoutingDecision decision = score >= 5
                ? AiRoutingDecision.INVOKE_AI_IF_ENABLED
                : score >= 3
                        ? AiRoutingDecision.OPTIONAL_AI_REVIEW
                        : AiRoutingDecision.USE_LOCAL;
        return new AiRoutingAssessment(score, decision, reasons);
    }

    private static boolean contains(OfflineTranslationDraft draft, String text) {
        return draft.assessment().reasons().stream().anyMatch(reason -> reason.contains(text));
    }
}
