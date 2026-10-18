package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TextSimilarity;
import com.ssmt.tm.TranslationEntry;
import com.ssmt.tm.TranslationMemoryException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For blank project entries the library cannot settle by an exact, unambiguous match: conflicting exact translations
 * and fuzzy matches. Candidates (human-approved library entries only) are ranked by {@link CandidateRanker}; the best
 * is proposed with its score and applied, as POLICY_APPROVED, only when the ranking threshold and every approval rule
 * pass. A fuzzy candidate (different source text) is applied only when the ranking configuration says so.
 */
public final class MemoryMatchSuggester {
    /**
     * One proposal.
     *
     * @param key project entry key
     * @param source the unit's source text
     * @param text proposed translation
     * @param score ranking score from 0 to 1
     * @param exactSource whether the library entry has exactly this source text
     * @param breakdown weighted contribution of each feature
     * @param applied whether it was applied without a person
     * @param note why it was or was not applied
     */
    public record Suggestion(String sourceFile, String key, String source, String text, double score,
            boolean exactSource, Map<String, Double> breakdown, boolean applied, String note) {
        public Suggestion {
            breakdown = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(breakdown));
        }
    }

    private static final Set<TranslationProvenance> TRUSTED = Set.of(TranslationProvenance.HUMAN_EDITED,
            TranslationProvenance.AUTHOR_LOCALIZATION, TranslationProvenance.MANUAL_IMPORT);
    private static final String SOURCE_LANGUAGE = "zh";
    private static final String TARGET_LANGUAGE = "en";
    private final CandidateRanker ranker;
    private final PolicyApproval approval = PolicyApproval.withDefaultRules();

    public MemoryMatchSuggester(CandidateRanker ranker) {
        this.ranker = ranker;
    }

    /** Proposals for every blank entry whose library match is conflicting or fuzzy; entries are not changed. */
    public List<Suggestion> suggest(LocalizationProject project, Path library) throws ProjectException {
        if (library == null || !Files.isRegularFile(library) || project.methodDocument() == null) {
            return List.of();
        }
        List<TranslationEntry> pool = new ArrayList<>();
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(library)) {
            for (TranslationEntry entry : memory.findAll()) {
                if (TRUSTED.contains(entry.provenance()) && entry.sourceLanguage().equals(SOURCE_LANGUAGE)
                        && entry.targetLanguage().equals(TARGET_LANGUAGE)) {
                    pool.add(entry);
                }
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException("Could not read the translation library: " + exception.getMessage(), exception);
        }
        Map<String, List<TranslationEntry>> bySource = new HashMap<>();
        pool.forEach(entry -> bySource.computeIfAbsent(entry.sourceText(), ignored -> new ArrayList<>()).add(entry));
        Map<String, JsonNode> units = new HashMap<>();
        project.methodDocument().path("entries").forEach(unit -> units.put(unit.path("id").asText(), unit));
        Map<String, String> glossary = PolicyApproval.glossaryOf(project);
        List<Suggestion> suggestions = new ArrayList<>();
        for (ProjectEntry entry : project.entries()) {
            if (!entry.translatedText().isBlank() || entry.originalText().isBlank() || !entry.key().startsWith("bf:")) {
                continue;
            }
            JsonNode unit = units.get(entry.key().substring(3));
            if (unit == null) {
                continue;
            }
            List<CandidateRanker.Candidate> candidates = candidates(entry, unit, pool, bySource);
            if (candidates.isEmpty() || distinctExact(candidates) == 1 && onlyExact(candidates)) {
                continue; // an unambiguous exact match is not this class's business
            }
            var ranked = ranker.rank(candidates);
            var suggestion = ranker.suggestion(ranked);
            if (suggestion == null) {
                continue;
            }
            suggestions.add(decide(entry, unit, ranked, suggestion, glossary));
        }
        return List.copyOf(suggestions);
    }

    private Suggestion decide(ProjectEntry entry, JsonNode unit, List<CandidateRanker.Ranked> ranked,
            CandidateRanker.Ranked suggestion, Map<String, String> glossary) {
        boolean exact = suggestion.origin().equals("library-exact");
        String note;
        boolean applied = false;
        var automatic = ranker.autoApplicable(ranked);
        String role = unit.path("role").asText("TEXT");
        if (automatic == null) {
            note = "score " + String.format(java.util.Locale.ROOT, "%.2f", suggestion.score())
                    + " is below the threshold or too close to the runner-up";
        } else if (!exact && !ranker.autoApplyFuzzy()) {
            note = "fuzzy match: proposed only";
        } else {
            String resolved = BridgeForgePlaceholders.resolve(entry.originalText(), automatic.text());
            var decision = resolved == null ? new PolicyApproval.Decision(false, "placeholders differ")
                    : approval.evaluate(role, unit.path("kind").asText(), entry.originalText(), resolved, glossary);
            applied = decision.approved();
            note = applied ? "applied by rules " + approval.rulesVersion() : decision.reason();
        }
        return new Suggestion(entry.sourceFile().toString().replace('\\', '/'), entry.key(), entry.originalText(),
                suggestion.text(), suggestion.score(), exact, suggestion.breakdown(), applied, note);
    }

    /** Applies the applied suggestions as POLICY_APPROVED translations. */
    public LocalizationProject applyTo(LocalizationProject project, List<Suggestion> suggestions) {
        Map<String, String> chosen = new HashMap<>();
        suggestions.stream().filter(Suggestion::applied).forEach(suggestion -> chosen.put(suggestion.key(),
                BridgeForgePlaceholders.resolve(suggestion.source(), suggestion.text())));
        return project.withEntries(project.entries().stream()
                .map(entry -> chosen.get(entry.key()) == null ? entry
                        : entry.withTranslation(chosen.get(entry.key()), TranslationProvenance.POLICY_APPROVED))
                .toList());
    }

    private List<CandidateRanker.Candidate> candidates(ProjectEntry entry, JsonNode unit, List<TranslationEntry> pool,
            Map<String, List<TranslationEntry>> bySource) {
        Map<String, Map<String, Double>> features = new LinkedHashMap<>();
        Map<String, String> origins = new HashMap<>();
        for (TranslationEntry match : bySource.getOrDefault(entry.originalText(), List.of())) {
            merge(features, origins, match, entry, unit, 1.0, "library-exact");
        }
        Set<String> grams = bigrams(entry.originalText());
        for (TranslationEntry match : pool) {
            if (match.sourceText().equals(entry.originalText()) || !plausible(entry.originalText(), match, grams)) {
                continue;
            }
            double similarity = TextSimilarity.score(entry.originalText(), match.sourceText());
            if (similarity >= ranker.fuzzyFloor()) {
                merge(features, origins, match, entry, unit, similarity, "library-fuzzy");
            }
        }
        List<CandidateRanker.Candidate> result = new ArrayList<>();
        features.forEach((text, values) -> result.add(new CandidateRanker.Candidate(text,
                origins.get(text), values)));
        return result;
    }

    private static boolean plausible(String source, TranslationEntry match, Set<String> grams) {
        int left = source.length();
        int right = match.sourceText().length();
        if (Math.abs(left - right) > Math.max(2, left / 3)) {
            return false;
        }
        Set<String> other = bigrams(match.sourceText());
        long shared = grams.stream().filter(other::contains).count();
        return shared * 2 >= Math.max(1, Math.min(grams.size(), other.size()));
    }

    private static Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        for (int index = 0; index + 1 < text.length(); index++) {
            grams.add(text.substring(index, index + 2));
        }
        return grams;
    }

    private static void merge(Map<String, Map<String, Double>> features, Map<String, String> origins,
            TranslationEntry match, ProjectEntry entry, JsonNode unit, double similarity, String origin) {
        Map<String, Double> measured = new LinkedHashMap<>();
        measured.put("similarity", similarity);
        String context = match.context();
        int hash = context.indexOf('#');
        if (hash > 0) {
            String file = context.substring(0, hash).replace('\\', '/');
            String own = entry.sourceFile().toString().replace('\\', '/');
            measured.put("same_file", file.equals(own) ? 1.0 : name(file).equals(name(own)) ? 0.5 : 0.0);
            String key = context.substring(hash + 1);
            if (key.startsWith("bf:csv:") && unit.path("kind").asText().equals("csv")) {
                int fieldCut = key.lastIndexOf(':');
                int rowCut = key.indexOf('#', "bf:csv:".length());
                if (fieldCut > rowCut && rowCut > 0) {
                    measured.put("same_field", key.substring(fieldCut + 1)
                            .equals(unit.path("context").path("column").asText()) ? 1.0 : 0.0);
                    String row = key.substring(rowCut + 1, fieldCut);
                    String own2 = unit.path("context").path("row").asText();
                    measured.put("neighbours", row.equals(own2) ? 1.0 : commonPrefix(row, own2) >= 3 ? 0.5 : 0.0);
                }
            }
        }
        features.merge(match.translatedText(), measured, (old, added) -> {
            Map<String, Double> best = new LinkedHashMap<>(old);
            added.forEach((name, value) -> best.merge(name, value, Math::max));
            return best;
        });
        origins.merge(match.translatedText(), origin, (old, added) -> old.equals("library-exact") ? old : added);
    }

    private static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static int commonPrefix(String left, String right) {
        int length = 0;
        while (length < left.length() && length < right.length() && left.charAt(length) == right.charAt(length)) {
            length++;
        }
        return length;
    }

    private static long distinctExact(List<CandidateRanker.Candidate> candidates) {
        return candidates.stream().filter(candidate -> candidate.origin().equals("library-exact")).count();
    }

    private static boolean onlyExact(List<CandidateRanker.Candidate> candidates) {
        return candidates.stream().allMatch(candidate -> candidate.origin().equals("library-exact"));
    }
}
