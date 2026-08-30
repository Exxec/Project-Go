package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.ai.OfflineTranslationAttempt;
import com.ssmt.ai.OfflineTranslationDraft;
import com.ssmt.ai.OfflineTranslationOrigin;
import com.ssmt.ai.ProviderGenerationMetadata;
import com.ssmt.ai.TranslationAssessment;
import com.ssmt.ai.TranslationConfidence;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationMemoryApprovedGlossaryTest {
    @TempDir Path temporary;

    @Test
    void returnsOnlyUnambiguousExactApprovedTranslationAndStoresAcceptedDraft() throws Exception {
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(temporary.resolve("glossary.db"))) {
            var glossary = new TranslationMemoryApprovedGlossary(memory);
            var request = new AiTranslationRequest("Flux", "en", "es", "weapon", "");

            assertThat(glossary.find(request)).isEmpty();
            glossary.approve(request, "Flujo");

            assertThat(glossary.find(request)).contains("Flujo");
            assertThat(memory.findAll()).singleElement()
                    .extracting(entry -> entry.provenance())
                    .isEqualTo(TranslationProvenance.HUMAN_EDITED);
        }
    }

    @Test
    void conflictingExactTranslationsAreNotTreatedAsGlossaryHit() throws Exception {
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(temporary.resolve("conflict.db"))) {
            memory.create(new TranslationDraft(
                    "Flux", "en", "es", "Flujo", "weapon", TranslationProvenance.HUMAN_EDITED));
            memory.create(new TranslationDraft(
                    "Flux", "en", "es", "Corriente", "campaign", TranslationProvenance.HUMAN_EDITED));
            var glossary = new TranslationMemoryApprovedGlossary(memory);

            assertThat(glossary.find(new AiTranslationRequest("Flux", "en", "es", "", "")))
                    .isEmpty();
        }
    }

    @Test
    void unreviewedMachineDraftIsNotAnApprovedGlossaryHit() throws Exception {
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(temporary.resolve("draft.db"))) {
            memory.create(new TranslationDraft(
                    "Flux", "en", "es", "Flujo", "",
                    TranslationProvenance.AI_TRANSLATED));
            var glossary = new TranslationMemoryApprovedGlossary(memory);

            assertThat(glossary.find(new AiTranslationRequest("Flux", "en", "es", "", "")))
                    .isEmpty();
        }
    }

    @Test
    void approvalRetainsMachineProviderLineageBesideHumanProvenance() throws Exception {
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(temporary.resolve("lineage.db"))) {
            var glossary = new TranslationMemoryApprovedGlossary(memory);
            var request = new AiTranslationRequest("源", "zh", "en", "tooltip", "");
            var metadata = new ProviderGenerationMetadata(
                    "translate-locally", "zh-en-base", "0.0.1",
                    Instant.parse("2026-08-02T12:00:00Z"), true);
            var attempt = new OfflineTranslationAttempt(
                    OfflineTranslationOrigin.TRANSLATE_LOCALLY,
                    "Source",
                    new TranslationAssessment(
                            TranslationConfidence.UNCERTAIN, List.of("AI refinement requested")),
                    metadata);
            var draft = new OfflineTranslationDraft(
                    request, "Source", OfflineTranslationOrigin.TRANSLATE_LOCALLY, true, "",
                    attempt.assessment(), List.of(attempt));

            glossary.approve(draft);

            var entry = memory.findAll().getFirst();
            assertThat(entry.provenance()).isEqualTo(TranslationProvenance.HUMAN_EDITED);
            assertThat(memory.findGenerationMetadata(entry.id()).orElseThrow())
                    .satisfies(stored -> {
                        assertThat(stored.providerId()).isEqualTo("translate-locally");
                        assertThat(stored.modelOrLanguagePackage()).isEqualTo("zh-en-base");
                        assertThat(stored.providerVersion()).isEqualTo("0.0.1");
                        assertThat(stored.aiRefined()).isTrue();
                        assertThat(stored.reviewStatus().name()).isEqualTo("APPROVED");
                    });
        }
    }
}
