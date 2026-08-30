package com.ssmt.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conservative detector for the observed FSF {@code aEP}/{@code aEP_En} layout.
 */
public final class AuthorLocalizationDetector {
    private static final String SOURCE_ROOT = "aEP";
    private static final String ENGLISH_ROOT = "aEP_En";

    /**
     * Detects entries whose relative suffix and stable key match exactly.
     *
     * @param entries extracted project entries
     * @return paired, unmatched, and ambiguous findings
     */
    public AuthorLocalizationReport detect(List<ProjectEntry> entries) {
        Map<String, List<ProjectEntry>> sources = namespace(entries, SOURCE_ROOT);
        Map<String, List<ProjectEntry>> translations = namespace(entries, ENGLISH_ROOT);
        List<AuthorLocalizationPair> pairs = new ArrayList<>();
        List<ProjectEntry> unmatched = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        java.util.Set<String> identities = new java.util.TreeSet<>();
        identities.addAll(sources.keySet());
        identities.addAll(translations.keySet());
        for (String identity : identities) {
            List<ProjectEntry> left = sources.getOrDefault(identity, List.of());
            List<ProjectEntry> right = translations.getOrDefault(identity, List.of());
            if (left.size() == 1 && right.size() == 1) {
                pairs.add(new AuthorLocalizationPair(left.getFirst(), right.getFirst()));
            } else if (left.size() > 1 || right.size() > 1) {
                ambiguous.add(identity);
            } else {
                unmatched.addAll(left);
                unmatched.addAll(right);
            }
        }
        unmatched.sort(entryOrder());
        return new AuthorLocalizationReport(pairs, unmatched, ambiguous);
    }

    private static Map<String, List<ProjectEntry>> namespace(
            List<ProjectEntry> entries,
            String root) {
        Map<String, List<ProjectEntry>> values = new LinkedHashMap<>();
        for (ProjectEntry entry : List.copyOf(entries)) {
            Path path = entry.sourceFile();
            if (path.getNameCount() < 2 || !root.equals(path.getName(0).toString())) {
                continue;
            }
            String suffix = path.subpath(1, path.getNameCount())
                    .toString().replace('\\', '/');
            values.computeIfAbsent(suffix + "#" + entry.key(), ignored -> new ArrayList<>())
                    .add(entry);
        }
        return values;
    }

    private static Comparator<ProjectEntry> entryOrder() {
        return Comparator.comparing(
                        (ProjectEntry entry) ->
                                entry.sourceFile().toString().replace('\\', '/'))
                .thenComparing(ProjectEntry::key);
    }
}
