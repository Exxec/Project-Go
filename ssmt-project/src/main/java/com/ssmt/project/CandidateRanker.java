package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks competing translations for one unit instead of leaving a reviewer to choose: same file, same column or path,
 * same neighbouring ids and edit distance between the sources, each weighted by data ({@code ranking-rules.json}).
 * The top candidate is proposed with its score; it may be applied automatically only when the score reaches the
 * configured threshold, it leads the runner-up by the configured margin, and the approval rules also pass.
 */
public final class CandidateRanker {
    /**
     * One competing translation.
     *
     * @param text the translation
     * @param origin where it came from (translation memory, record, ...)
     * @param features feature name to value from 0 to 1; a feature that could not be measured is absent
     */
    public record Candidate(String text, String origin, Map<String, Double> features) {
        public Candidate {
            features = Map.copyOf(features);
        }
    }

    /**
     * A candidate with its score.
     *
     * @param breakdown the weighted contribution of each measured feature
     */
    public record Ranked(String text, String origin, double score, Map<String, Double> breakdown) {
        public Ranked {
            breakdown = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(breakdown));
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<String, Double> weights = new LinkedHashMap<>();
    private final double autoApplyThreshold;
    private final double margin;
    private final double minimumScore;
    private final boolean autoApplyFuzzy;
    private final double fuzzyFloor;

    private CandidateRanker(JsonNode rules) {
        rules.path("weights").fields().forEachRemaining(field -> weights.put(field.getKey(),
                field.getValue().asDouble()));
        autoApplyThreshold = rules.path("auto_apply_threshold").asDouble(0.85);
        margin = rules.path("auto_apply_margin").asDouble(0.05);
        minimumScore = rules.path("minimum_suggestion_score").asDouble(0.4);
        autoApplyFuzzy = rules.path("auto_apply_fuzzy").asBoolean(false);
        fuzzyFloor = rules.path("fuzzy_floor").asDouble(0.6);
    }

    /** The configuration shipped with the program. */
    public static CandidateRanker withDefaultRules() {
        try (InputStream input = CandidateRanker.class.getResourceAsStream("/com/ssmt/project/ranking-rules.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing ranking-rules.json");
            }
            return new CandidateRanker(JSON.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read ranking-rules.json", exception);
        }
    }

    /** Caller-supplied configuration (same schema as {@code ranking-rules.json}). */
    public static CandidateRanker fromRules(JsonNode rules) {
        return new CandidateRanker(rules);
    }

    public double autoApplyThreshold() {
        return autoApplyThreshold;
    }

    public boolean autoApplyFuzzy() {
        return autoApplyFuzzy;
    }

    public double fuzzyFloor() {
        return fuzzyFloor;
    }

    /** Ranks candidates best first; equal scores keep the order of the input (the caller sorts for determinism). */
    public List<Ranked> rank(List<Candidate> candidates) {
        List<Ranked> ranked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            double total = 0;
            double measured = 0;
            Map<String, Double> breakdown = new LinkedHashMap<>();
            for (var weight : weights.entrySet()) {
                Double value = candidate.features().get(weight.getKey());
                if (value != null) {
                    double part = weight.getValue() * Math.max(0, Math.min(1, value));
                    breakdown.put(weight.getKey(), part);
                    total += part;
                    measured += weight.getValue();
                }
            }
            ranked.add(new Ranked(candidate.text(), candidate.origin(), measured == 0 ? 0 : total / measured,
                    breakdown));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());
        return List.copyOf(ranked);
    }

    /** The top candidate when it is good enough to propose at all. */
    public Ranked suggestion(List<Ranked> ranked) {
        return ranked.isEmpty() || ranked.getFirst().score() < minimumScore ? null : ranked.getFirst();
    }

    /**
     * The top candidate when it may be applied without a person as far as ranking goes: the score reaches the
     * threshold and leads the runner-up by the margin. The approval rules still have to pass.
     */
    public Ranked autoApplicable(List<Ranked> ranked) {
        if (ranked.isEmpty() || ranked.getFirst().score() < autoApplyThreshold) {
            return null;
        }
        if (ranked.size() > 1 && ranked.getFirst().score() - ranked.get(1).score() < margin
                && !ranked.get(1).text().equals(ranked.getFirst().text())) {
            return null;
        }
        return ranked.getFirst();
    }
}
