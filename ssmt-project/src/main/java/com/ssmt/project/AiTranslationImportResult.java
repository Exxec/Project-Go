package com.ssmt.project;

/**
 * Result of applying a reviewable external-AI translation response.
 *
 * @param project updated project
 * @param importedEntries number of matched nonblank translations
 * @param skippedEntries number of blank or omitted translations
 * @param patchNameUpdated whether the translated mod name changed the patch name
 * @param policyApproved entries marked POLICY_APPROVED by the approval rules
 * @param policyReview one line per entry the rules sent to review, with the rule that failed
 * @param suppliedEntryIds exact response identities with a validated, nonblank supplied target
 */
public record AiTranslationImportResult(
        LocalizationProject project,
        int importedEntries,
        int skippedEntries,
        boolean patchNameUpdated,
        int policyApproved,
        java.util.List<String> policyReview,
        java.util.Set<String> suppliedEntryIds) {
    public AiTranslationImportResult {
        policyReview = java.util.List.copyOf(policyReview);
        suppliedEntryIds = java.util.Set.copyOf(suppliedEntryIds);
    }

    public AiTranslationImportResult(LocalizationProject project, int importedEntries, int skippedEntries,
            boolean patchNameUpdated, int policyApproved, java.util.List<String> policyReview) {
        this(project, importedEntries, skippedEntries, patchNameUpdated, policyApproved, policyReview,
                java.util.Set.of());
    }

    public AiTranslationImportResult(LocalizationProject project, int importedEntries, int skippedEntries,
            boolean patchNameUpdated) {
        this(project, importedEntries, skippedEntries, patchNameUpdated, 0, java.util.List.of());
    }
}
