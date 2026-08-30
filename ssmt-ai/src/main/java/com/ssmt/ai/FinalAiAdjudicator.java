package com.ssmt.ai;

import com.ssmt.validation.TranslationValidator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Applies deterministic routing to one retained offline draft and optional final AI. */
public final class FinalAiAdjudicator {
    private final AiRoutingHeuristic routing = new AiRoutingHeuristic();
    private final AiAdjudicationPromptBuilder prompts = new AiAdjudicationPromptBuilder();
    private final TranslationValidator validator = new TranslationValidator();

    public FinalAiAdjudicationResult adjudicate(
            OfflineTranslationDraft draft,
            TranslationMode mode,
            Optional<AiTranslationProvider> provider,
            AiProviderLocation providerLocation,
            boolean remoteDisclosureAccepted,
            String styleBrief) throws AiProviderException {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(mode, "mode");
        Optional<AiTranslationProvider> configured = Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerLocation, "providerLocation");
        AiRoutingAssessment assessment = routing.assess(draft);
        boolean shouldInvoke = mode.allowsAi()
                && (assessment.decision() == AiRoutingDecision.INVOKE_AI_IF_ENABLED
                    || mode == TranslationMode.AI_ASSISTED
                        && assessment.decision() == AiRoutingDecision.OPTIONAL_AI_REVIEW);
        if (!shouldInvoke) {
            return new FinalAiAdjudicationResult(
                    draft.translatedText(), FinalAiAdjudicationStatus.LOCAL_RETAINED,
                    draft.requiresReview(), assessment, "AI routing did not request a provider");
        }
        if (configured.isEmpty()) {
            return new FinalAiAdjudicationResult(
                    draft.translatedText(), FinalAiAdjudicationStatus.UNRESOLVED,
                    true, assessment, "Final AI is not configured; retained local draft offline");
        }
        if (providerLocation == AiProviderLocation.REMOTE && !remoteDisclosureAccepted) {
            throw new AiProviderException(
                    "Remote AI disclosure and explicit consent are required before sending text");
        }
        List<OfflineTranslationAttempt> attempts = draft.attempts().isEmpty()
                ? List.of(new OfflineTranslationAttempt(
                        draft.origin(), draft.translatedText(), draft.assessment()))
                : draft.attempts();
        AiTranslationRequest request = prompts.build(
                draft.request(), attempts, assessment.reasons(), styleBrief);
        String translated = configured.orElseThrow().translate(request);
        if (translated == null || translated.isBlank()
                || !validator.validate(draft.request().sourceText(), translated).isEmpty()) {
            return new FinalAiAdjudicationResult(
                    draft.translatedText(), FinalAiAdjudicationStatus.UNRESOLVED,
                    true, assessment, "Final AI returned a blank or structurally invalid draft");
        }
        return new FinalAiAdjudicationResult(
                translated, FinalAiAdjudicationStatus.AI_DRAFT,
                true, assessment, providerLocation == AiProviderLocation.REMOTE
                        ? "Remote AI draft produced after explicit disclosure consent"
                        : "Local AI draft produced");
    }
}
