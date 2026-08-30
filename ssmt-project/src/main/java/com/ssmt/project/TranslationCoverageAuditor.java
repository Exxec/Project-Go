package com.ssmt.project;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only translation-completion statistics, overall and per source file.
 */
public final class TranslationCoverageAuditor {
    /**
     * Audits entries without changing the project.
     *
     * @param project project to inspect
     * @return overall and per-file translation coverage
     */
    public TranslationCoverageReport audit(LocalizationProject project) {
        Map<Path, int[]> perFile = new TreeMap<>(Comparator.comparing(Path::toString));
        int total = 0;
        int translated = 0;
        for (ProjectEntry entry : project.entries()) {
            total++;
            boolean isTranslated = !entry.translatedText().isBlank();
            if (isTranslated) {
                translated++;
            }
            int[] counts = perFile.computeIfAbsent(entry.sourceFile(), ignored -> new int[2]);
            counts[0]++;
            if (isTranslated) {
                counts[1]++;
            }
        }
        List<FileTranslationCoverage> files = perFile.entrySet().stream()
                .map(entry -> new FileTranslationCoverage(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
        return new TranslationCoverageReport(total, translated, files);
    }
}
