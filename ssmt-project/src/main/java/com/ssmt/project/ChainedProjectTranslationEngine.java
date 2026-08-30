package com.ssmt.project;

import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiProviderLocation;
import com.ssmt.ai.AiTranslationProvider;
import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.ai.FinalAiAdjudicationStatus;
import com.ssmt.ai.FinalAiAdjudicator;
import com.ssmt.ai.OfflineTranslationChain;
import com.ssmt.ai.OfflineTranslationDraft;
import com.ssmt.ai.ProviderGenerationMetadata;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.TranslationGenerationMetadata;
import com.ssmt.tm.TranslationReviewStatus;
import com.ssmt.validation.TranslationValidator;
import java.time.Instant;
import java.util.Optional;

/** Connects the offline chain and bounded final-AI adjudicator for project routing. */
public final class ChainedProjectTranslationEngine implements ProjectEntryTranslationEngine {
    private final OfflineTranslationChain offline;
    private final FinalAiAdjudicator finalAi;
    private final ProjectTranslationSettings settings;
    private final Optional<AiTranslationProvider> aiProvider;
    private final AiProviderLocation aiLocation;
    private final TranslationValidator validator = new TranslationValidator();

    public ChainedProjectTranslationEngine(
            OfflineTranslationChain offline,
            ProjectTranslationSettings settings,
            Optional<AiTranslationProvider> aiProvider,
            AiProviderLocation aiLocation) {
        this.offline = java.util.Objects.requireNonNull(offline, "offline");
        this.finalAi = new FinalAiAdjudicator();
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.aiProvider = java.util.Objects.requireNonNull(aiProvider, "aiProvider");
        this.aiLocation = java.util.Objects.requireNonNull(aiLocation, "aiLocation");
    }

    @Override
    public ProjectEntryTranslation translate(AiTranslationRequest request)
            throws AiProviderException {
        OfflineTranslationDraft local;
        try {
            local = offline.translate(request);
        } catch (AiProviderException localFailure) {
            return fallbackToAi(request, localFailure);
        }
        var result = finalAi.adjudicate(
                local, settings.mode(), aiProvider, aiLocation,
                settings.remoteDisclosureAccepted(), settings.styleBrief());
        if (result.status() == FinalAiAdjudicationStatus.AI_DRAFT) {
            return new ProjectEntryTranslation(
                    result.translatedText(), TranslationProvenance.AI_TRANSLATED,
                    Optional.of(new TranslationGenerationMetadata(
                            "configured-final-ai", "", "", Instant.now(), true,
                            TranslationReviewStatus.DRAFT)),
                    aiLocation == AiProviderLocation.REMOTE ? "Remote AI" : "Local AI",
                    false);
        }
        ProviderGenerationMetadata generated = local.attempts().isEmpty()
                ? new ProviderGenerationMetadata("approved-glossary", "", "", Instant.now(), false)
                : local.attempts().getLast().generationMetadata();
        TranslationProvenance provenance = switch (local.origin()) {
            case APPROVED_GLOSSARY -> TranslationProvenance.HUMAN_EDITED;
            case ARGOS_TRANSLATE -> TranslationProvenance.ARGOS_TRANSLATED;
            case TRANSLATE_LOCALLY -> TranslationProvenance.TRANSLATE_LOCALLY;
        };
        return new ProjectEntryTranslation(
                result.translatedText(), provenance,
                Optional.of(new TranslationGenerationMetadata(
                        generated.providerId(), generated.modelOrLanguagePackage(),
                        generated.providerVersion(), generated.generatedAt(),
                        generated.aiRefined(), TranslationReviewStatus.DRAFT)),
                generated.providerId(),
                result.status() == FinalAiAdjudicationStatus.UNRESOLVED);
    }

    @Override
    public Optional<ProjectEntryTranslation> findApproved(AiTranslationRequest request)
            throws AiProviderException {
        return offline.findApproved(request).map(draft -> new ProjectEntryTranslation(
                draft.translatedText(), TranslationProvenance.HUMAN_EDITED,
                Optional.empty(), "Approved exact TM/glossary", false));
    }

    /**
     * Falls back to the configured AI provider when both local providers
     * failed (e.g. neither Argos Translate nor TranslateLocally is
     * installed), so an unavailable local chain does not block translation
     * for a project that has AI configured. Bypasses {@link FinalAiAdjudicator}'s
     * escalation heuristic entirely, since that model assumes an existing
     * local candidate to escalate from and there isn't one here.
     */
    private ProjectEntryTranslation fallbackToAi(
            AiTranslationRequest request, AiProviderException localFailure)
            throws AiProviderException {
        if (!settings.mode().allowsAi() || aiProvider.isEmpty()) {
            throw localFailure;
        }
        if (aiLocation == AiProviderLocation.REMOTE && !settings.remoteDisclosureAccepted()) {
            throw new AiProviderException(
                    "Remote AI disclosure and explicit consent are required before sending text");
        }
        String translated;
        try {
            translated = aiProvider.orElseThrow().translate(request);
        } catch (AiProviderException aiFailure) {
            throw new AiProviderException(
                    "Local translation unavailable (" + localFailure.getMessage()
                            + "); AI fallback also failed: " + aiFailure.getMessage(),
                    localFailure);
        }
        if (translated == null || translated.isBlank()
                || !validator.validate(request.sourceText(), translated).isEmpty()) {
            throw new AiProviderException(
                    "Local translation unavailable (" + localFailure.getMessage()
                            + "); AI fallback returned a blank or structurally invalid draft",
                    localFailure);
        }
        return new ProjectEntryTranslation(
                translated, TranslationProvenance.AI_TRANSLATED,
                Optional.of(new TranslationGenerationMetadata(
                        "configured-final-ai-fallback", "", "", Instant.now(), true,
                        TranslationReviewStatus.DRAFT)),
                aiLocation == AiProviderLocation.REMOTE ? "Remote AI" : "Local AI",
                false);
    }
}
