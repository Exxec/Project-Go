package com.ssmt.project;

import java.util.List;

/**
 * Overall and per-file translation completion counts for one project.
 *
 * @param totalEntries total extracted entries
 * @param translatedEntries entries with non-blank translated text
 * @param files deterministic per-source-file breakdown
 */
public record TranslationCoverageReport(
        int totalEntries,
        int translatedEntries,
        List<FileTranslationCoverage> files) {

    public TranslationCoverageReport {
        files = List.copyOf(files);
    }

    /**
     * @return fraction translated in {@code [0, 1]}; {@code 1.0} when there are no entries
     */
    public double translatedFraction() {
        return totalEntries == 0 ? 1.0 : (double) translatedEntries / totalEntries;
    }
}
