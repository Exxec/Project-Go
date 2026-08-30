package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OfflineTranslationChainTest {
    private static final AiTranslationRequest REQUEST =
            new AiTranslationRequest("Hello", "en", "es", "menu", "");

    @Test
    void stopsAtApprovedGlossaryHit() throws Exception {
        RecordingGlossary glossary = new RecordingGlossary("Hola aprobada");
        var chain = new OfflineTranslationChain(
                glossary,
                request -> { throw new AssertionError("Argos must not run"); },
                request -> { throw new AssertionError("TranslateLocally must not run"); });

        OfflineTranslationDraft draft = chain.translate(REQUEST);

        assertThat(draft.translatedText()).isEqualTo("Hola aprobada");
        assertThat(draft.origin()).isEqualTo(OfflineTranslationOrigin.APPROVED_GLOSSARY);
        assertThat(draft.requiresReview()).isFalse();
        assertThat(draft.assessment().confidence()).isEqualTo(TranslationConfidence.HIGH);
    }

    @Test
    void usesArgosBeforeTranslateLocally() throws Exception {
        var chain = new OfflineTranslationChain(
                new RecordingGlossary(null),
                request -> "Hola por Argos",
                request -> { throw new AssertionError("fallback must not run"); });

        OfflineTranslationDraft draft = chain.translate(REQUEST);

        assertThat(draft.origin()).isEqualTo(OfflineTranslationOrigin.ARGOS_TRANSLATE);
        assertThat(draft.requiresReview()).isTrue();
        assertThat(draft.assessment().confidence()).isEqualTo(TranslationConfidence.HIGH);
        assertThat(draft.attempts()).hasSize(1);
    }

    @Test
    void fallsBackToTranslateLocallyWhenArgosFails() throws Exception {
        var chain = new OfflineTranslationChain(
                new RecordingGlossary(null),
                request -> { throw new AiProviderException("missing model"); },
                request -> "Hola local");

        OfflineTranslationDraft draft = chain.translate(REQUEST);

        assertThat(draft.translatedText()).isEqualTo("Hola local");
        assertThat(draft.origin()).isEqualTo(OfflineTranslationOrigin.TRANSLATE_LOCALLY);
        assertThat(draft.fallbackReason()).contains("missing model");
    }

    @Test
    void approvalIsTheOnlyOperationThatFeedsTheGlossary() throws Exception {
        RecordingGlossary glossary = new RecordingGlossary(null);
        var chain = new OfflineTranslationChain(glossary, request -> "Hola", request -> "unused");
        OfflineTranslationDraft draft = chain.translate(REQUEST);
        assertThat(glossary.approved).isFalse();

        chain.approve(draft);

        assertThat(glossary.approved).isTrue();
        assertThat(glossary.approvedText).isEqualTo("Hola");
    }

    @Test
    void reportsBothProviderFailures() {
        var chain = new OfflineTranslationChain(
                new RecordingGlossary(null),
                request -> { throw new AiProviderException("argos failed"); },
                request -> { throw new AiProviderException("tl failed"); });

        assertThatThrownBy(() -> chain.translate(REQUEST))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Argos Translate")
                .hasMessageContaining("TranslateLocally");
    }

    @Test
    void uncertainArgosCandidateEscalatesUsingTheOriginalSource() throws Exception {
        AiTranslationRequest difficult = new AiTranslationRequest(
                "A long and deliberately difficult sentence about the Domain and its lost "
                        + "technology that needs context before anyone should trust a draft.",
                "en", "es", "campaign dialogue", "Domain: preserve lore meaning");
        AtomicReference<String> translateLocallySource = new AtomicReference<>();
        var chain = new OfflineTranslationChain(
                new RecordingGlossary(null),
                request -> "Borrador de Argos",
                request -> {
                    translateLocallySource.set(request.sourceText());
                    return "Borrador local independiente";
                });

        OfflineTranslationDraft draft = chain.translate(difficult);

        assertThat(translateLocallySource).hasValue(difficult.sourceText());
        assertThat(draft.origin()).isEqualTo(OfflineTranslationOrigin.TRANSLATE_LOCALLY);
        assertThat(draft.assessment().confidence()).isEqualTo(TranslationConfidence.UNCERTAIN);
        assertThat(draft.assessment().reasons()).anyMatch(reason -> reason.contains("disagree"));
        assertThat(draft.attempts()).extracting(OfflineTranslationAttempt::origin)
                .containsExactly(
                        OfflineTranslationOrigin.ARGOS_TRANSLATE,
                        OfflineTranslationOrigin.TRANSLATE_LOCALLY);
    }

    @Test
    void unsafePlaceholderDamageEscalatesAndRemainsUnsafe() throws Exception {
        AiTranslationRequest formatted =
                new AiTranslationRequest("Damage: %s", "en", "es", "tooltip", "");
        RecordingGlossary glossary = new RecordingGlossary(null);
        var chain = new OfflineTranslationChain(
                glossary, request -> "Daño", request -> "Daño local");

        OfflineTranslationDraft draft = chain.translate(formatted);

        assertThat(draft.assessment().confidence()).isEqualTo(TranslationConfidence.UNSAFE);
        assertThat(draft.assessment().reasons()).anyMatch(reason -> reason.contains("PRINTF"));
        assertThat(glossary.approved).isFalse();
    }

    @Test
    void permitsCancellationBeforeStartingTheNextTranslationStage() {
        var chain = new OfflineTranslationChain(
                new RecordingGlossary(null), request -> "Hola", request -> "Hola local");
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> chain.translate(REQUEST))
                    .isInstanceOf(AiProviderException.class)
                    .hasMessageContaining("cancelled");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void exactDuplicateRequestUsesSessionDraftCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var chain = new OfflineTranslationChain(
                new RecordingGlossary(null),
                request -> {
                    calls.incrementAndGet();
                    return "Hola";
                },
                request -> "unused");

        assertThat(chain.translate(REQUEST)).isSameAs(chain.translate(REQUEST));
        assertThat(calls).hasValue(1);
    }

    @Test
    void unsafeDraftCannotBeApprovedIntoGlossary() throws Exception {
        RecordingGlossary glossary = new RecordingGlossary(null);
        var chain = new OfflineTranslationChain(
                glossary, request -> "Daño", request -> "Daño local");
        OfflineTranslationDraft draft = chain.translate(
                new AiTranslationRequest("Damage: %s\nReady", "en", "es", "", ""));

        assertThatThrownBy(() -> chain.approve(draft))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("unsafe");
        assertThat(glossary.approved).isFalse();
    }

    private static final class RecordingGlossary implements ApprovedGlossary {
        private final String translation;
        private boolean approved;
        private String approvedText;

        private RecordingGlossary(String translation) {
            this.translation = translation;
        }

        @Override
        public Optional<String> find(AiTranslationRequest request) {
            return Optional.ofNullable(translation);
        }

        @Override
        public void approve(AiTranslationRequest request, String translatedText) {
            approved = true;
            approvedText = translatedText;
        }
    }
}
