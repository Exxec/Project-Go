package com.ssmt.project;

import java.util.List;

/**
 * Structured deterministic translation-consistency finding.
 *
 * @param type finding classification
 * @param normalizedSource compared normalized source
 * @param entries conflicting entries
 * @param translations distinct translations
 */
public record ConsistencyFinding(
        ConsistencyFindingType type,
        String normalizedSource,
        List<ProjectEntry> entries,
        List<String> translations) {

    public ConsistencyFinding {
        entries = List.copyOf(entries);
        translations = List.copyOf(translations);
    }
}
