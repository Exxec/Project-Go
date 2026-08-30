package com.ssmt.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Report-only contextual evidence. Findings deliberately carry no routing weight. */
public final class RoutingEvidenceDetector {
    public List<String> inspect(AiTranslationRequest request, String translatedText) {
        List<String> evidence = new ArrayList<>();
        String context = request.context().toLowerCase(Locale.ROOT);
        if (context.contains("dialog") || context.contains("conversation")) {
            evidence.add("context suggests dialogue");
        }
        if (context.contains("description") || request.sourceText().length() > 240) {
            evidence.add("context suggests long-form description or lore");
        }
        if (context.contains("weapon") || context.contains("ship system")) {
            evidence.add("context suggests mechanics-sensitive text");
        }
        if (!request.glossary().isBlank() && translatedText != null
                && request.glossary().lines().noneMatch(translatedText::contains)) {
            evidence.add("approved terminology may be absent from the draft");
        }
        if (translatedText != null && translatedText.codePoints().anyMatch(
                point -> Character.UnicodeScript.of(point)
                        == Character.UnicodeScript.HAN)) {
            evidence.add("draft may retain untranslated Han-script text or a proper noun");
        }
        return List.copyOf(evidence);
    }
}
