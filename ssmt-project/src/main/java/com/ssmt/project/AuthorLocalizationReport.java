package com.ssmt.project;

import java.util.List;

/**
 * Deterministic findings from conservative parallel-localization detection.
 *
 * @param pairs unambiguous pairs
 * @param unmatched entries in a recognized namespace without a counterpart
 * @param ambiguous identities with more than one possible counterpart
 */
public record AuthorLocalizationReport(
        List<AuthorLocalizationPair> pairs,
        List<ProjectEntry> unmatched,
        List<String> ambiguous) {

    public AuthorLocalizationReport {
        pairs = List.copyOf(pairs);
        unmatched = List.copyOf(unmatched);
        ambiguous = List.copyOf(ambiguous);
    }
}
