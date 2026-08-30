package com.ssmt.project;

import com.ssmt.ai.TranslationMode;
import java.util.Objects;
import java.util.OptionalLong;

/** Bounded project-translation settings with conservative defaults. */
public record ProjectTranslationSettings(
        String sourceLanguage,
        String targetLanguage,
        TranslationMode mode,
        PreferredLocalProvider preferredLocalProvider,
        int maximumWorkerThreads,
        int maximumBatchSize,
        OptionalLong maximumGpuMemoryMiB,
        String terminology,
        String styleBrief,
        boolean retainUnreviewedDrafts,
        boolean remoteDisclosureAccepted) {
    public ProjectTranslationSettings {
        if (sourceLanguage == null || sourceLanguage.isBlank()
                || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("source and target languages are required");
        }
        mode = Objects.requireNonNull(mode, "mode");
        preferredLocalProvider = Objects.requireNonNull(
                preferredLocalProvider, "preferredLocalProvider");
        if (maximumWorkerThreads < 1 || maximumBatchSize < 1) {
            throw new IllegalArgumentException("worker and batch limits must be positive");
        }
        maximumGpuMemoryMiB = maximumGpuMemoryMiB == null
                ? OptionalLong.empty() : maximumGpuMemoryMiB;
        terminology = Objects.requireNonNullElse(terminology, "");
        styleBrief = Objects.requireNonNullElse(styleBrief, "");
    }

    public static ProjectTranslationSettings conservativeZhToEn() {
        return new ProjectTranslationSettings(
                "zh", "en", TranslationMode.SMART_DEFAULT,
                PreferredLocalProvider.ARGOS, 1, 32, OptionalLong.empty(),
                "", "", true, false);
    }
}
