package com.ssmt.project;

/** Read-only summary shown before Project Go creates a personal copy. */
public record ProjectBuildPreview(
        int translatedEntries, int untranslatedEntries, int sourceFiles) {
    public ProjectBuildPreview {
        if (translatedEntries < 0 || untranslatedEntries < 0 || sourceFiles < 0) {
            throw new IllegalArgumentException("Preview counts must not be negative");
        }
    }

    /** @return total entries represented by this preview */
    public int totalEntries() {
        return translatedEntries + untranslatedEntries;
    }
}
