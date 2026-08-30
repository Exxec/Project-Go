package com.ssmt.ai;

import java.util.List;

/** Assesses a candidate using source evidence and earlier independent attempts. */
@FunctionalInterface
public interface TranslationConfidenceEvaluator {
    TranslationAssessment assess(
            AiTranslationRequest request,
            OfflineTranslationOrigin origin,
            String candidate,
            List<OfflineTranslationAttempt> priorAttempts);
}
