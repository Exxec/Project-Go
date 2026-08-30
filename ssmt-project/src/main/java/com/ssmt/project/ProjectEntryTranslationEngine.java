package com.ssmt.project;

import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiTranslationRequest;

/** Provider-chain boundary used by the project-level router. */
@FunctionalInterface
public interface ProjectEntryTranslationEngine {
    ProjectEntryTranslation translate(AiTranslationRequest request) throws AiProviderException;

    /** Returns an approved exact reuse without machine inference when available. */
    default java.util.Optional<ProjectEntryTranslation> findApproved(
            AiTranslationRequest request) throws AiProviderException {
        return java.util.Optional.empty();
    }
}
