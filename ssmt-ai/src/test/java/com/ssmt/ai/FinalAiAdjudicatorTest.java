package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FinalAiAdjudicatorTest {
    private final FinalAiAdjudicator adjudicator = new FinalAiAdjudicator();

    @Test
    void localOnlyNeverCallsConfiguredProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FinalAiAdjudicationResult result = adjudicator.adjudicate(
                uncertainDraft(), TranslationMode.LOCAL_ONLY,
                Optional.of(request -> {
                    calls.incrementAndGet();
                    return "AI";
                }),
                AiProviderLocation.REMOTE, false, "style");

        assertThat(calls).hasValue(0);
        assertThat(result.status()).isEqualTo(FinalAiAdjudicationStatus.LOCAL_RETAINED);
    }

    @Test
    void absentProviderKeepsUncertaintyOffline() throws Exception {
        FinalAiAdjudicationResult result = adjudicator.adjudicate(
                uncertainDraft(), TranslationMode.AI_ASSISTED, Optional.empty(),
                AiProviderLocation.REMOTE, false, "");

        assertThat(result.status()).isEqualTo(FinalAiAdjudicationStatus.UNRESOLVED);
        assertThat(result.detail()).contains("not configured");
    }

    @Test
    void remoteProviderRequiresExplicitConsentBeforeReceivingText() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> adjudicator.adjudicate(
                uncertainDraft(), TranslationMode.AI_ASSISTED,
                Optional.of(request -> {
                    calls.incrementAndGet();
                    return "AI";
                }),
                AiProviderLocation.REMOTE, false, ""))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("consent");
        assertThat(calls).hasValue(0);
    }

    @Test
    void sendsBothLocalCandidatesAndReturnsUnapprovedValidAiDraft() throws Exception {
        final AiTranslationRequest[] captured = new AiTranslationRequest[1];
        FinalAiAdjudicationResult result = adjudicator.adjudicate(
                uncertainDraft(), TranslationMode.AI_ASSISTED,
                Optional.of(request -> {
                    captured[0] = request;
                    return "Polished %s\nline";
                }),
                AiProviderLocation.REMOTE, true, "terse naval voice");

        assertThat(result.status()).isEqualTo(FinalAiAdjudicationStatus.AI_DRAFT);
        assertThat(result.requiresReview()).isTrue();
        assertThat(captured[0].preparedPrompt())
                .contains("Argos draft %s\nline", "TranslateLocally draft %s\nline",
                        "terse naval voice");
    }

    @Test
    void invalidAiOutputLeavesUncertaintyUnresolved() throws Exception {
        FinalAiAdjudicationResult result = adjudicator.adjudicate(
                uncertainDraft(), TranslationMode.AI_ASSISTED,
                Optional.of(request -> "Lost syntax"), AiProviderLocation.LOCAL, true, "");

        assertThat(result.status()).isEqualTo(FinalAiAdjudicationStatus.UNRESOLVED);
        assertThat(result.translatedText()).isEqualTo("Argos draft %s\nline");
    }

    private static OfflineTranslationDraft uncertainDraft() {
        AiTranslationRequest request = new AiTranslationRequest(
                "Source %s\nline " + "源".repeat(130),
                "zh", "en", "ship tooltip", "CR=CR");
        TranslationAssessment uncertain = new TranslationAssessment(
                TranslationConfidence.UNCERTAIN,
                List.of("independent local candidates disagree"));
        List<OfflineTranslationAttempt> attempts = List.of(
                new OfflineTranslationAttempt(OfflineTranslationOrigin.ARGOS_TRANSLATE,
                        "Argos draft %s\nline", uncertain),
                new OfflineTranslationAttempt(OfflineTranslationOrigin.TRANSLATE_LOCALLY,
                        "TranslateLocally draft %s\nline", uncertain));
        return new OfflineTranslationDraft(request, attempts.getFirst().translatedText(),
                OfflineTranslationOrigin.ARGOS_TRANSLATE, true, "", uncertain, attempts);
    }
}
