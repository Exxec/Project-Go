package com.ssmt.project;

import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.core.CancellationToken;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.TranslationGenerationMetadata;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, cancellable project-level router over an already configured provider engine. */
public final class ProjectTranslationCoordinator {
    public ProjectTranslationResult translate(
            LocalizationProject project,
            ProjectTranslationSettings settings,
            Map<String, String> acceptedFuzzyTranslations,
            ProjectEntryTranslationEngine engine,
            CancellationToken cancellation) throws ProjectException {
        return translate(project, settings, acceptedFuzzyTranslations, engine, cancellation,
                ProjectTranslationCheckpointSink.NONE);
    }

    public ProjectTranslationResult translate(
            LocalizationProject project,
            ProjectTranslationSettings settings,
            Map<String, String> acceptedFuzzyTranslations,
            ProjectEntryTranslationEngine engine,
            CancellationToken cancellation,
            ProjectTranslationCheckpointSink checkpoints) throws ProjectException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(settings, "settings");
        Map<String, String> fuzzy = Map.copyOf(
                Objects.requireNonNull(acceptedFuzzyTranslations, "acceptedFuzzyTranslations"));
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(checkpoints, "checkpoints");
        List<ProjectEntry> result = new ArrayList<>(project.entries());
        Map<AiTranslationRequest, ProjectEntryTranslation> requestCache = new LinkedHashMap<>();
        Map<String, TranslationGenerationMetadata> metadata = new LinkedHashMap<>();
        LinkedHashSet<String> backends = new LinkedHashSet<>();
        int translated = 0;
        int preserved = 0;
        int reused = 0;
        int unresolved = 0;
        for (int batchStart = 0; batchStart < result.size();
                batchStart += settings.maximumBatchSize()) {
            cancellation.throwIfCancellationRequested();
            int batchEnd = Math.min(result.size(), batchStart + settings.maximumBatchSize());
            for (int index = batchStart; index < batchEnd; index++) {
                ProjectEntry entry = result.get(index);
                if (!entry.translatedText().isBlank()) {
                    preserved++;
                    continue;
                }
                String identity = identity(entry);
                AiTranslationRequest request = new AiTranslationRequest(
                        entry.originalText(), settings.sourceLanguage(),
                        settings.targetLanguage(), context(project, entry),
                        settings.terminology());
                try {
                    var approved = engine.findApproved(request);
                    if (approved.isPresent()) {
                        ProjectEntryTranslation exact = approved.orElseThrow();
                        result.set(index, entry.withTranslation(
                                exact.translatedText(), exact.provenance()));
                        translated++;
                        backends.add(exact.backend());
                        continue;
                    }
                } catch (AiProviderException exception) {
                    throw new ProjectException(
                            "Approved translation lookup failed at " + identity, exception);
                }
                String acceptedFuzzy = fuzzy.get(identity);
                if (acceptedFuzzy != null && !acceptedFuzzy.isBlank()) {
                    result.set(index, entry.withTranslation(
                            acceptedFuzzy, TranslationProvenance.FUZZY_MATCH));
                    translated++;
                    continue;
                }
                ProjectEntryTranslation candidate = requestCache.get(request);
                if (candidate == null) {
                    try {
                        candidate = engine.translate(request);
                    } catch (AiProviderException exception) {
                        throw new ProjectException(
                                "Translation provider failed at " + identity, exception);
                    }
                    requestCache.put(request, candidate);
                } else {
                    reused++;
                }
                result.set(index, entry.withTranslation(
                        candidate.translatedText(), candidate.provenance()));
                translated++;
                if (candidate.unresolved()) {
                    unresolved++;
                }
                backends.add(candidate.backend());
                if (settings.retainUnreviewedDrafts()) {
                    candidate.generationMetadata().ifPresent(value -> metadata.put(identity, value));
                }
            }
            checkpoints.save(project.withEntries(result), batchEnd);
        }
        return new ProjectTranslationResult(
                project.withEntries(result), translated, preserved, reused, unresolved,
                new ArrayList<>(backends), metadata);
    }

    private static String context(LocalizationProject project, ProjectEntry entry) {
        String path = entry.sourceFile().toString().replace('\\', '/');
        int extension = path.lastIndexOf('.');
        String type = extension < 0 ? "unknown" : path.substring(extension + 1);
        return "mod=" + project.sourceModId() + "; file=" + path
                + "; contentType=" + type + "; internalId=" + entry.key();
    }

    private static String identity(ProjectEntry entry) {
        return entry.sourceFile().toString().replace('\\', '/') + "#" + entry.key();
    }
}
