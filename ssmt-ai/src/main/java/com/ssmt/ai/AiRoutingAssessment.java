package com.ssmt.ai;

import java.util.List;
import java.util.Objects;

/** Deterministic AI-routing score with observable reasons. */
public record AiRoutingAssessment(
        int score,
        AiRoutingDecision decision,
        List<String> reasons) {

    public AiRoutingAssessment {
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        decision = Objects.requireNonNull(decision, "decision");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }
}
