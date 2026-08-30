package com.ssmt.project;

import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.ai.ApprovedGlossary;
import com.ssmt.ai.OfflineTranslationDraft;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import com.ssmt.tm.TranslationMemoryException;
import com.ssmt.tm.TranslationGenerationMetadata;
import com.ssmt.tm.TranslationReviewStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Uses unambiguous exact translation-memory entries as an approved glossary. */
public final class TranslationMemoryApprovedGlossary implements ApprovedGlossary {
    private final SqliteTranslationMemory memory;

    public TranslationMemoryApprovedGlossary(SqliteTranslationMemory memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    @Override
    public Optional<String> find(AiTranslationRequest request) throws AiProviderException {
        try {
            List<String> matches = memory.findExactApprovedTranslations(
                    request.sourceText(), request.sourceLanguage(), request.targetLanguage());
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        } catch (TranslationMemoryException exception) {
            throw new AiProviderException("Could not search the approved glossary", exception);
        }
    }

    @Override
    public void approve(AiTranslationRequest request, String translatedText)
            throws AiProviderException {
        try {
            memory.upsertAll(List.of(new TranslationDraft(
                    request.sourceText(),
                    request.sourceLanguage(),
                    request.targetLanguage(),
                    translatedText,
                    request.context(),
                    TranslationProvenance.HUMAN_EDITED)));
        } catch (TranslationMemoryException | IllegalArgumentException exception) {
            throw new AiProviderException("Could not update the approved glossary", exception);
        }
    }

    @Override
    public void approve(OfflineTranslationDraft draft) throws AiProviderException {
        approve(draft.request(), draft.translatedText());
        if (draft.attempts().isEmpty()) {
            return;
        }
        var generated = draft.attempts().getLast().generationMetadata();
        try {
            memory.recordGenerationMetadata(
                    draft.request().sourceText(),
                    draft.request().sourceLanguage(),
                    draft.request().targetLanguage(),
                    draft.request().context(),
                    new TranslationGenerationMetadata(
                            generated.providerId(),
                            generated.modelOrLanguagePackage(),
                            generated.providerVersion(),
                            generated.generatedAt(),
                            generated.aiRefined(),
                            TranslationReviewStatus.APPROVED));
        } catch (TranslationMemoryException exception) {
            throw new AiProviderException(
                    "Translation was approved but its provider metadata could not be stored",
                    exception);
        }
    }
}
