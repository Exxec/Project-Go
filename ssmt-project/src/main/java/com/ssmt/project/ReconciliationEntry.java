package com.ssmt.project;

import java.nio.file.Path;
import java.util.List;

/**
 * One deterministic project-refresh finding.
 *
 * @param status reconciliation classification
 * @param sourceFile current or former relative source file
 * @param key current or former stable key
 * @param originalText current source text, or former text for removals
 * @param previousTranslation preserved draft candidate, if any
 * @param suggestions non-applied translation suggestions
 */
public record ReconciliationEntry(
        ReconciliationStatus status,
        Path sourceFile,
        String key,
        String originalText,
        String previousTranslation,
        List<String> suggestions) {
    public ReconciliationEntry {
        suggestions = List.copyOf(suggestions).stream().distinct().sorted().toList();
    }

    @Override
    public List<String> suggestions() {
        return List.copyOf(suggestions);
    }
}
