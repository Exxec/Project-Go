package com.ssmt.project;

/**
 * Result of applying a reviewable external-AI translation response.
 *
 * @param project updated project
 * @param importedEntries number of matched nonblank translations
 * @param skippedEntries number of blank or omitted translations
 * @param patchNameUpdated whether the translated mod name changed the patch name
 */
public record AiTranslationImportResult(
        LocalizationProject project,
        int importedEntries,
        int skippedEntries,
        boolean patchNameUpdated) {
}
