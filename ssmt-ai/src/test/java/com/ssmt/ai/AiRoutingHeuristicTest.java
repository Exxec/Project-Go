package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiRoutingHeuristicTest {
    private final AiRoutingHeuristic heuristic = new AiRoutingHeuristic();

    @Test
    void shortValidLocalDraftStaysLocal() {
        AiTranslationRequest request = new AiTranslationRequest("武器", "zh", "en", "label", "");
        OfflineTranslationDraft draft = draft(
                request, "Weapon", TranslationAssessment.high(), List.of());

        assertThat(heuristic.assess(draft))
                .isEqualTo(new AiRoutingAssessment(0, AiRoutingDecision.USE_LOCAL, List.of()));
    }

    @Test
    void observableSignalsProduceDeterministicThresholds() {
        AiTranslationRequest request = new AiTranslationRequest(
                "A".repeat(140), "zh", "en", "lore description", "");
        TranslationAssessment assessment = new TranslationAssessment(
                TranslationConfidence.UNCERTAIN,
                List.of("independent local candidates disagree"));
        OfflineTranslationDraft draft = draft(request, "B".repeat(140), assessment, List.of());

        assertThat(heuristic.assess(draft)).satisfies(result -> {
            assertThat(result.score()).isEqualTo(4);
            assertThat(result.decision()).isEqualTo(AiRoutingDecision.OPTIONAL_AI_REVIEW);
            assertThat(result.reasons()).containsExactly(
                    "long descriptive source +2",
                    "independent local candidates disagree +2");
        });
    }

    @Test
    void unsafeLongTextCrossesAutomaticAiThresholdButDoesNotImplyAcceptance() {
        AiTranslationRequest request = new AiTranslationRequest(
                "Damage %s " + "A".repeat(130), "en", "fr", "mechanics", "");
        TranslationAssessment assessment = new TranslationAssessment(
                TranslationConfidence.UNSAFE,
                List.of("PRINTF_PLACEHOLDER_MISMATCH: conversions differ"));

        assertThat(heuristic.assess(draft(request, "Dommages", assessment, List.of())))
                .satisfies(result -> {
                    assertThat(result.score()).isEqualTo(5);
                    assertThat(result.decision()).isEqualTo(AiRoutingDecision.INVOKE_AI_IF_ENABLED);
                });
    }

    @Test
    void modesKeepAiOptionalAndExplicit() {
        assertThat(TranslationMode.LOCAL_ONLY.allowsAi()).isFalse();
        assertThat(TranslationMode.SMART_DEFAULT.allowsAi()).isTrue();
        assertThat(TranslationMode.AI_ASSISTED.allowsAi()).isTrue();
    }

    private static OfflineTranslationDraft draft(
            AiTranslationRequest request,
            String text,
            TranslationAssessment assessment,
            List<OfflineTranslationAttempt> attempts) {
        return new OfflineTranslationDraft(
                request, text, OfflineTranslationOrigin.ARGOS_TRANSLATE,
                true, "", assessment, attempts);
    }
}
