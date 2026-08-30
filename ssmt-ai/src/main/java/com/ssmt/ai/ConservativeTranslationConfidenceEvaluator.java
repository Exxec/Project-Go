package com.ssmt.ai;

import com.ssmt.validation.TranslationValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conservative structural and plausibility assessment for local MT candidates. */
public final class ConservativeTranslationConfidenceEvaluator
        implements TranslationConfidenceEvaluator {
    private static final int DIFFICULT_LENGTH = 120;
    private final TranslationValidator validator = new TranslationValidator();

    @Override
    public TranslationAssessment assess(
            AiTranslationRequest request,
            OfflineTranslationOrigin origin,
            String candidate,
            List<OfflineTranslationAttempt> priorAttempts) {
        List<String> unsafe = validator.validate(request.sourceText(), candidate).stream()
                .map(issue -> issue.code() + ": " + issue.message())
                .toList();
        if (!unsafe.isEmpty()) {
            return new TranslationAssessment(TranslationConfidence.UNSAFE, unsafe);
        }

        List<String> uncertain = new ArrayList<>();
        if (!request.sourceLanguage().equalsIgnoreCase(request.targetLanguage())
                && normalize(request.sourceText()).equals(normalize(candidate))) {
            uncertain.add("candidate is unchanged from source text");
        }
        if (request.sourceText().length() >= 8) {
            double ratio = (double) candidate.length() / request.sourceText().length();
            if (ratio < 0.35 || ratio > 3.0) {
                uncertain.add("candidate length is implausible relative to source");
            }
        }

        if (!priorAttempts.isEmpty()) {
            String previous = priorAttempts.getLast().translatedText();
            if (normalize(previous).equals(normalize(candidate))) {
                return uncertain.isEmpty()
                        ? TranslationAssessment.high()
                        : new TranslationAssessment(TranslationConfidence.UNCERTAIN, uncertain);
            }
            uncertain.add("independent local candidates disagree");
        } else {
            if (request.sourceText().length() > DIFFICULT_LENGTH) {
                uncertain.add("source exceeds conservative difficulty length");
            }
            if (request.sourceText().contains("\n") || request.sourceText().contains("\r")) {
                uncertain.add("multiline source requires additional context review");
            }
        }

        return uncertain.isEmpty()
                ? TranslationAssessment.high()
                : new TranslationAssessment(TranslationConfidence.UNCERTAIN, uncertain);
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
