package com.ssmt.ai;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Glossary-first offline draft chain with an Argos-to-TranslateLocally fallback. */
public final class OfflineTranslationChain {
    private static final int MAX_CACHED_DRAFTS = 1024;
    private final ApprovedGlossary glossary;
    private final AiTranslationProvider argos;
    private final AiTranslationProvider translateLocally;
    private final OfflineTranslationOrigin firstOrigin;
    private final OfflineTranslationOrigin secondOrigin;
    private final TranslationConfidenceEvaluator confidence;
    private final Map<AiTranslationRequest, OfflineTranslationDraft> draftCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<AiTranslationRequest, OfflineTranslationDraft> eldest) {
                    return size() > MAX_CACHED_DRAFTS;
                }
            };

    public OfflineTranslationChain(
            ApprovedGlossary glossary,
            AiTranslationProvider argos,
            AiTranslationProvider translateLocally) {
        this(glossary, argos, translateLocally,
                OfflineTranslationOrigin.ARGOS_TRANSLATE,
                OfflineTranslationOrigin.TRANSLATE_LOCALLY,
                new ConservativeTranslationConfidenceEvaluator());
    }

    /** Creates a chain with an explicitly selected first local provider. */
    public OfflineTranslationChain(
            ApprovedGlossary glossary,
            AiTranslationProvider argos,
            AiTranslationProvider translateLocally,
            boolean preferTranslateLocally) {
        this(glossary,
                preferTranslateLocally ? translateLocally : argos,
                preferTranslateLocally ? argos : translateLocally,
                preferTranslateLocally
                        ? OfflineTranslationOrigin.TRANSLATE_LOCALLY
                        : OfflineTranslationOrigin.ARGOS_TRANSLATE,
                preferTranslateLocally
                        ? OfflineTranslationOrigin.ARGOS_TRANSLATE
                        : OfflineTranslationOrigin.TRANSLATE_LOCALLY,
                new ConservativeTranslationConfidenceEvaluator());
    }

    public OfflineTranslationChain(
            ApprovedGlossary glossary,
            AiTranslationProvider argos,
            AiTranslationProvider translateLocally,
            TranslationConfidenceEvaluator confidence) {
        this(glossary, argos, translateLocally,
                OfflineTranslationOrigin.ARGOS_TRANSLATE,
                OfflineTranslationOrigin.TRANSLATE_LOCALLY, confidence);
    }

    private OfflineTranslationChain(
            ApprovedGlossary glossary,
            AiTranslationProvider first,
            AiTranslationProvider second,
            OfflineTranslationOrigin firstOrigin,
            OfflineTranslationOrigin secondOrigin,
            TranslationConfidenceEvaluator confidence) {
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.argos = Objects.requireNonNull(first, "first");
        this.translateLocally = Objects.requireNonNull(second, "second");
        this.firstOrigin = Objects.requireNonNull(firstOrigin, "firstOrigin");
        this.secondOrigin = Objects.requireNonNull(secondOrigin, "secondOrigin");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
    }

    public synchronized OfflineTranslationDraft translate(AiTranslationRequest request)
            throws AiProviderException {
        Objects.requireNonNull(request, "request");
        ensureNotCancelled();
        OfflineTranslationDraft cached = draftCache.get(request);
        if (cached != null) {
            return cached;
        }
        Optional<String> approved = glossary.find(request);
        if (approved.isPresent()) {
            return cache(new OfflineTranslationDraft(
                    request, approved.orElseThrow(), OfflineTranslationOrigin.APPROVED_GLOSSARY,
                    false, "", TranslationAssessment.high(), List.of()));
        }
        ensureNotCancelled();
        try {
            String argosText = requireCandidate(
                    argos.translate(request), firstOrigin);
            TranslationAssessment argosAssessment = confidence.assess(
                    request, firstOrigin, argosText, List.of());
            OfflineTranslationAttempt argosAttempt = new OfflineTranslationAttempt(
                    firstOrigin,
                    argosText,
                    argosAssessment,
                    attribution(argos, request, firstOrigin));
            if (argosAssessment.confidence() == TranslationConfidence.HIGH) {
                return cache(machineDraft(request, argosAttempt, "", List.of(argosAttempt)));
            }
            try {
                ensureNotCancelled();
                String localText = requireCandidate(
                        translateLocally.translate(request),
                        secondOrigin);
                TranslationAssessment localAssessment = confidence.assess(
                        request,
                        secondOrigin,
                        localText,
                        List.of(argosAttempt));
                OfflineTranslationAttempt localAttempt = new OfflineTranslationAttempt(
                        secondOrigin,
                        localText,
                        localAssessment,
                        attribution(
                                translateLocally,
                                request,
                                secondOrigin));
                return cache(machineDraft(
                        request, localAttempt, "Argos confidence: " + argosAssessment.confidence(),
                        List.of(argosAttempt, localAttempt)));
            } catch (AiProviderException locallyFailure) {
                if (Thread.currentThread().isInterrupted()) {
                    throw locallyFailure;
                }
                return cache(machineDraft(
                        request, argosAttempt,
                        "TranslateLocally escalation failed: " + locallyFailure.getMessage(),
                        List.of(argosAttempt)));
            }
        } catch (AiProviderException argosFailure) {
            try {
                ensureNotCancelled();
                String localText = requireCandidate(
                        translateLocally.translate(request),
                        OfflineTranslationOrigin.TRANSLATE_LOCALLY);
                TranslationAssessment localAssessment = confidence.assess(
                        request, secondOrigin,
                        localText, List.of());
                OfflineTranslationAttempt localAttempt = new OfflineTranslationAttempt(
                        secondOrigin,
                        localText,
                        localAssessment,
                        attribution(
                                translateLocally,
                                request,
                                secondOrigin));
                return cache(machineDraft(request, localAttempt,
                        "Argos Translate: " + argosFailure.getMessage(),
                        List.of(localAttempt)));
            } catch (AiProviderException locallyFailure) {
                if (Thread.currentThread().isInterrupted()) {
                    throw locallyFailure;
                }
                throw new AiProviderException(
                        "Argos Translate failed (" + argosFailure.getMessage()
                                + "); TranslateLocally failed (" + locallyFailure.getMessage() + ")",
                        locallyFailure);
            }
        }
    }

    /** Looks up an approved exact value without invoking either machine provider. */
    public synchronized Optional<OfflineTranslationDraft> findApproved(AiTranslationRequest request)
            throws AiProviderException {
        Objects.requireNonNull(request, "request");
        Optional<String> approved = glossary.find(request);
        return approved.map(value -> new OfflineTranslationDraft(
                request, value, OfflineTranslationOrigin.APPROVED_GLOSSARY,
                false, "", TranslationAssessment.high(), List.of()));
    }

    /** Promotes a reviewed draft into the approved glossary. */
    public synchronized void approve(OfflineTranslationDraft draft) throws AiProviderException {
        Objects.requireNonNull(draft, "draft");
        if (draft.assessment().confidence() == TranslationConfidence.UNSAFE) {
            throw new AiProviderException(
                    "Cannot approve an unsafe draft; correct protected syntax and line breaks first");
        }
        glossary.approve(draft);
        draftCache.remove(draft.request());
    }

    private static String requireCandidate(
            String text,
            OfflineTranslationOrigin origin) throws AiProviderException {
        if (text == null || text.isBlank()) {
            throw new AiProviderException(origin + " returned an empty translation");
        }
        return text.strip();
    }

    private static void ensureNotCancelled() throws AiProviderException {
        if (Thread.currentThread().isInterrupted()) {
            throw new AiProviderException("Offline translation was cancelled");
        }
    }

    private static ProviderGenerationMetadata attribution(
            AiTranslationProvider provider,
            AiTranslationRequest request,
            OfflineTranslationOrigin origin) {
        if (provider instanceof AttributedTranslationProvider attributed) {
            return attributed.attribution(request);
        }
        String providerId = origin == OfflineTranslationOrigin.ARGOS_TRANSLATE
                ? "argos-translate"
                : "translate-locally";
        return new ProviderGenerationMetadata(
                providerId, "", "", java.time.Instant.now(), false);
    }

    private static OfflineTranslationDraft machineDraft(
            AiTranslationRequest request,
            OfflineTranslationAttempt selected,
            String fallbackReason,
            List<OfflineTranslationAttempt> attempts) {
        return new OfflineTranslationDraft(
                request,
                selected.translatedText(),
                selected.origin(),
                true,
                fallbackReason,
                selected.assessment(),
                attempts);
    }

    private OfflineTranslationDraft cache(OfflineTranslationDraft draft) {
        draftCache.put(draft.request(), draft);
        return draft;
    }
}
