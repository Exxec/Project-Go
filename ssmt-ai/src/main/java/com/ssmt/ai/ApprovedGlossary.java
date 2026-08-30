package com.ssmt.ai;

import java.util.Optional;

/** Approved exact translations used before any machine-translation provider. */
public interface ApprovedGlossary {
    Optional<String> find(AiTranslationRequest request) throws AiProviderException;

    void approve(AiTranslationRequest request, String translatedText) throws AiProviderException;

    default void approve(OfflineTranslationDraft draft) throws AiProviderException {
        approve(draft.request(), draft.translatedText());
    }
}
