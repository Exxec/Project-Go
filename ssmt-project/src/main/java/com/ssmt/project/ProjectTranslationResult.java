package com.ssmt.project;

import com.ssmt.tm.TranslationGenerationMetadata;
import java.util.List;
import java.util.Map;

/** Result and diagnostics from bounded project translation. */
public record ProjectTranslationResult(
        LocalizationProject project,
        int translatedEntries,
        int preservedEntries,
        int duplicateReuses,
        int unresolvedEntries,
        List<String> backendsUsed,
        Map<String, TranslationGenerationMetadata> retainedMetadata) {
    public ProjectTranslationResult {
        backendsUsed = List.copyOf(backendsUsed);
        retainedMetadata = Map.copyOf(retainedMetadata);
    }
}
