package com.ssmt.project;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only deterministic audit for inconsistent repeated translations.
 */
public final class TerminologyConsistencyAuditor {
    /**
     * Audits translated entries without changing the project.
     *
     * @param project project to inspect
     * @return structured findings in deterministic order
     */
    public List<ConsistencyFinding> audit(LocalizationProject project) {
        List<ConsistencyFinding> findings = new ArrayList<>();
        addFindings(findings, project.entries(), false);
        addFindings(findings, project.entries(), true);
        findings.sort(Comparator
                .comparing(ConsistencyFinding::normalizedSource)
                .thenComparing(finding -> finding.type().name()));
        return List.copyOf(findings);
    }

    /**
     * Counts exact, nonblank source units for glossary proposals, including untranslated units.
     * Method documents supply authoritative role classifications; identifiers are excluded.
     * Case, punctuation and whitespace variants remain distinct glossary keys even when the
     * consistency audit relates them. This observation never approves a term or translation.
     * Legacy projects without a method document use their extracted entries.
     *
     * @param project project to inspect without modification
     * @return immutable source counts in natural source order
     */
    public Map<String, Integer> sourceOccurrences(LocalizationProject project) {
        Map<String, Integer> counts = new TreeMap<>();
        var document = project.methodDocument();
        if (document == null) {
            for (ProjectEntry entry : project.entries()) {
                countSource(counts, entry.originalText());
            }
        } else {
            for (var entry : document.path("entries")) {
                if (!entry.path("role").asText("TEXT").equals("IDENTIFIER")) {
                    countSource(counts, entry.path("source").asText());
                }
            }
        }
        return Collections.unmodifiableMap(counts);
    }

    private static void countSource(Map<String, Integer> counts, String source) {
        if (!source.isBlank()) {
            counts.merge(source, 1, Integer::sum);
        }
    }

    private static void addFindings(
            List<ConsistencyFinding> findings,
            List<ProjectEntry> entries,
            boolean normalizedTerm) {
        Map<String, List<ProjectEntry>> groups = new TreeMap<>();
        for (ProjectEntry entry : entries) {
            if (entry.translatedText().isBlank()) {
                continue;
            }
            String source = normalizedTerm
                    ? normalize(entry.originalText())
                    : entry.originalText();
            String key = normalizedTerm
                    ? source + "\u0000" + contextClass(entry)
                    : source;
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<ProjectEntry>> group : groups.entrySet()) {
            List<String> translations = group.getValue().stream()
                    .map(ProjectEntry::translatedText)
                    .distinct()
                    .sorted()
                    .toList();
            boolean sourcesDiffer = group.getValue().stream()
                    .map(ProjectEntry::originalText).distinct().count() > 1;
            if (translations.size() > 1 && (!normalizedTerm || sourcesDiffer)) {
                List<ProjectEntry> sorted = group.getValue().stream()
                        .sorted(Comparator.comparing(
                                        (ProjectEntry value) -> value.sourceFile().toString())
                                .thenComparing(ProjectEntry::key))
                        .toList();
                findings.add(new ConsistencyFinding(
                        normalizedTerm
                                ? ConsistencyFindingType.NORMALIZED_TERM
                                : ConsistencyFindingType.EXACT_DUPLICATE_SOURCE,
                        normalize(group.getValue().getFirst().originalText()),
                        sorted,
                        translations));
            }
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{Zs}]+", "")
                .strip();
    }

    private static String contextClass(ProjectEntry entry) {
        String key = entry.key();
        int separator = Math.max(key.lastIndexOf(':'), key.lastIndexOf('/'));
        String field = separator < 0 ? key : key.substring(separator + 1);
        String file = entry.sourceFile().toString();
        int extension = file.lastIndexOf('.');
        return (extension < 0 ? "" : file.substring(extension).toLowerCase(Locale.ROOT))
                + "#" + field;
    }
}
