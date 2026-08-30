package com.ssmt.project;

import java.nio.file.Path;

/**
 * Translation completion counts for one source file.
 *
 * @param sourceFile mod-relative file the entries belong to
 * @param totalEntries number of extracted entries in this file
 * @param translatedEntries number of those entries with non-blank translated text
 */
public record FileTranslationCoverage(Path sourceFile, int totalEntries, int translatedEntries) {

    /**
     * @return fraction translated in {@code [0, 1]}; {@code 1.0} when there are no entries
     */
    public double translatedFraction() {
        return totalEntries == 0 ? 1.0 : (double) translatedEntries / totalEntries;
    }
}
