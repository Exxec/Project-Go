package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiProviderLocation;
import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.ai.ApprovedGlossary;
import com.ssmt.ai.OfflineTranslationChain;
import com.ssmt.ai.TranslationAssessment;
import com.ssmt.ai.TranslationConfidence;
import com.ssmt.ai.TranslationMode;
import com.ssmt.core.model.TranslationProvenance;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChainedProjectTranslationEngineTest {
    @Test
    void fallsBackToAiWhenBothLocalProvidersFailAndModeAllowsAi() throws Exception {
        OfflineTranslationChain offline = failingOfflineChain();
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", TranslationMode.SMART_DEFAULT, PreferredLocalProvider.ARGOS,
                1, 32, OptionalLong.empty(), "", "", true, false);
        ChainedProjectTranslationEngine engine = new ChainedProjectTranslationEngine(
                offline, settings, Optional.of(request -> "AI translation"),
                AiProviderLocation.LOCAL);

        ProjectEntryTranslation result = engine.translate(
                new AiTranslationRequest("source text", "zh", "en", "context", ""));

        assertThat(result.translatedText()).isEqualTo("AI translation");
        assertThat(result.provenance()).isEqualTo(TranslationProvenance.AI_TRANSLATED);
        assertThat(result.unresolved()).isFalse();
        assertThat(result.generationMetadata()).isPresent();
        assertThat(result.generationMetadata().orElseThrow().providerId())
                .isEqualTo("configured-final-ai-fallback");
    }

    @Test
    void rethrowsLocalFailureWhenModeIsLocalOnlyEvenWithAiConfigured() {
        OfflineTranslationChain offline = failingOfflineChain();
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", TranslationMode.LOCAL_ONLY, PreferredLocalProvider.ARGOS,
                1, 32, OptionalLong.empty(), "", "", true, false);
        AtomicInteger aiCalls = new AtomicInteger();
        ChainedProjectTranslationEngine engine = new ChainedProjectTranslationEngine(
                offline, settings, Optional.of(request -> {
                    aiCalls.incrementAndGet();
                    return "AI translation";
                }), AiProviderLocation.LOCAL);

        assertThatThrownBy(() -> engine.translate(
                new AiTranslationRequest("source text", "zh", "en", "context", "")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Argos")
                .hasMessageContaining("TranslateLocally");
        assertThat(aiCalls).hasValue(0);
    }

    @Test
    void rethrowsLocalFailureWhenNoAiProviderIsConfigured() {
        OfflineTranslationChain offline = failingOfflineChain();
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", TranslationMode.SMART_DEFAULT, PreferredLocalProvider.ARGOS,
                1, 32, OptionalLong.empty(), "", "", true, false);
        ChainedProjectTranslationEngine engine = new ChainedProjectTranslationEngine(
                offline, settings, Optional.empty(), AiProviderLocation.LOCAL);

        assertThatThrownBy(() -> engine.translate(
                new AiTranslationRequest("source text", "zh", "en", "context", "")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Argos");
    }

    @Test
    void fallbackStillRequiresRemoteConsentWhenBothLocalProvidersFail() {
        OfflineTranslationChain offline = failingOfflineChain();
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", TranslationMode.SMART_DEFAULT, PreferredLocalProvider.ARGOS,
                1, 32, OptionalLong.empty(), "", "", true, false);
        AtomicInteger remoteCalls = new AtomicInteger();
        ChainedProjectTranslationEngine engine = new ChainedProjectTranslationEngine(
                offline, settings, Optional.of(request -> {
                    remoteCalls.incrementAndGet();
                    return "Remote translation";
                }), AiProviderLocation.REMOTE);

        assertThatThrownBy(() -> engine.translate(
                new AiTranslationRequest("source text", "zh", "en", "context", "")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("consent");
        assertThat(remoteCalls).hasValue(0);
    }

    @Test
    void fallbackThrowsCombinedFailureWhenAiAlsoReturnsBlank() {
        OfflineTranslationChain offline = failingOfflineChain();
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", TranslationMode.SMART_DEFAULT, PreferredLocalProvider.ARGOS,
                1, 32, OptionalLong.empty(), "", "", true, false);
        ChainedProjectTranslationEngine engine = new ChainedProjectTranslationEngine(
                offline, settings, Optional.of(request -> ""), AiProviderLocation.LOCAL);

        assertThatThrownBy(() -> engine.translate(
                new AiTranslationRequest("source text", "zh", "en", "context", "")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Argos")
                .hasMessageContaining("fallback");
    }

    private static OfflineTranslationChain failingOfflineChain() {
        return new OfflineTranslationChain(
                emptyGlossary(),
                request -> {
                    throw new AiProviderException("Argos executable not found");
                },
                request -> {
                    throw new AiProviderException("TranslateLocally executable not found");
                });
    }

    @Test
    void projectEngineDoesNotTransmitToRemoteProviderWithoutConsent() {
        AtomicInteger remoteCalls = new AtomicInteger();
        OfflineTranslationChain offline = new OfflineTranslationChain(
                emptyGlossary(), request -> "Argos %s", request -> "Local %s",
                (request, origin, candidate, attempts) -> new TranslationAssessment(
                        TranslationConfidence.UNCERTAIN,
                        List.of("independent local candidates disagree")));
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", TranslationMode.AI_ASSISTED, PreferredLocalProvider.ARGOS,
                1, 32, OptionalLong.empty(), "", "", true, false);
        ChainedProjectTranslationEngine engine = new ChainedProjectTranslationEngine(
                offline, settings, Optional.of(request -> {
                    remoteCalls.incrementAndGet();
                    return "Remote %s";
                }), AiProviderLocation.REMOTE);

        assertThatThrownBy(() -> engine.translate(new AiTranslationRequest(
                "源 %s " + "长".repeat(130), "zh", "en", "context", "")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("consent");
        assertThat(remoteCalls).hasValue(0);
    }

    private static ApprovedGlossary emptyGlossary() {
        return new ApprovedGlossary() {
            @Override
            public Optional<String> find(AiTranslationRequest request) {
                return Optional.empty();
            }

            @Override
            public void approve(AiTranslationRequest request, String translatedText) {
                // Test glossary deliberately retains nothing.
            }
        };
    }
}
