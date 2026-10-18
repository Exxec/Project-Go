package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic data only. */
class CandidateRankingTest {
    private static final String SOURCE = "高级装甲";
    @TempDir Path temporary;

    private static CandidateRanker.Candidate candidate(String text, double file, double field, double neighbours,
            double similarity) {
        return new CandidateRanker.Candidate(text, "t", Map.of("same_file", file, "same_field", field,
                "neighbours", neighbours, "similarity", similarity));
    }

    @Test void theBestFeaturesWinAndTheLeaderIsAppliedOnlyAboveThresholdAndMargin() {
        var ranker = CandidateRanker.withDefaultRules();
        var ranked = ranker.rank(List.of(candidate("Weak", 0, 0, 0, 1), candidate("Strong", 1, 1, 1, 1)));
        assertThat(ranked).extracting(CandidateRanker.Ranked::text).containsExactly("Strong", "Weak");
        assertThat(ranked.getFirst().score()).isEqualTo(1.0);
        assertThat(ranker.autoApplicable(ranked).text()).isEqualTo("Strong");
        var close = ranker.rank(List.of(candidate("A", 1, 1, 1, 1), candidate("B", 1, 1, 1, 0.9)));
        assertThat(ranker.autoApplicable(close)).isNull(); // a lead of 0.025 is under the 0.05 margin
    }

    @Test void aNearTieIsNotAppliedAndAWeakLeaderIsNotEvenProposed() {
        var ranker = CandidateRanker.withDefaultRules();
        var tie = ranker.rank(List.of(candidate("A", 1, 1, 1, 1), candidate("B", 1, 1, 1, 1)));
        assertThat(ranker.autoApplicable(tie)).isNull();
        assertThat(ranker.suggestion(tie)).isNotNull();
        var weak = ranker.rank(List.of(candidate("A", 0, 0, 0, 0.3), candidate("B", 0, 0, 0, 0.1)));
        assertThat(ranker.suggestion(weak)).isNull();
        var onlyMeasured = ranker.rank(List.of(new CandidateRanker.Candidate("C", "t", Map.of("similarity", 0.9))));
        assertThat(onlyMeasured.getFirst().score()).isEqualTo(0.9);
    }

    @Test void thresholdAndWeightsAreData() throws Exception {
        var rules = new ObjectMapper().readTree("{\"weights\":{\"similarity\":1.0},\"auto_apply_threshold\":0.99,"
                + "\"auto_apply_margin\":0.0,\"minimum_suggestion_score\":0.0}");
        var strict = CandidateRanker.fromRules(rules);
        var ranked = strict.rank(List.of(new CandidateRanker.Candidate("A", "t", Map.of("similarity", 0.95))));
        assertThat(strict.autoApplicable(ranked)).isNull();
        assertThat(strict.autoApplyThreshold()).isEqualTo(0.99);
    }

    private Path mod(String name, String description) throws IOException {
        Path mod = temporary.resolve(name);
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"" + name + "\",\"name\":\"N\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/ships.csv"), "id,name,desc\nship1," + SOURCE + "," + description + "\n");
        return mod;
    }

    private Path library(TranslationDraft... drafts) throws Exception {
        Path library = temporary.resolve("tm-" + drafts.length + "-" + System.nanoTime() + ".db");
        try (var memory = SqliteTranslationMemory.open(library)) {
            memory.upsertAll(List.of(drafts));
        }
        return library;
    }

    private static TranslationDraft draft(String source, String target, String context,
            TranslationProvenance provenance) {
        return new TranslationDraft(source, "zh", "en", target, context, provenance);
    }

    @Test void conflictingExactTranslationsAreRankedBySameFileColumnAndRowAndAppliedByRules() throws Exception {
        Path mod = mod("rank1", "说明");
        var project = new LocalizationProjectService().createUsingBridgeForge(mod, "p", "P");
        Path library = library(
                draft(SOURCE, "Advanced armor", "data\\ships.csv#bf:csv:data/ships.csv#ship1:name",
                        TranslationProvenance.HUMAN_EDITED),
                draft(SOURCE, "Elite plating", "data\\other.csv#bf:csv:data/other.csv#zzz:title",
                        TranslationProvenance.HUMAN_EDITED));
        var suggester = new MemoryMatchSuggester(CandidateRanker.withDefaultRules());
        var suggestions = suggester.suggest(project, library);
        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.text()).isEqualTo("Advanced armor");
            assertThat(suggestion.exactSource()).isTrue();
            assertThat(suggestion.applied()).isTrue();
            assertThat(suggestion.score()).isGreaterThan(0.85);
            assertThat(suggestion.breakdown()).containsKeys("same_file", "same_field", "neighbours", "similarity");
        });
        var applied = suggester.applyTo(project, suggestions);
        assertThat(applied.entries()).filteredOn(entry -> entry.originalText().equals(SOURCE)).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.translatedText()).isEqualTo("Advanced armor");
                    assertThat(entry.provenance()).isEqualTo(TranslationProvenance.POLICY_APPROVED);
                });
        assertThat(applied.entries()).filteredOn(entry -> !entry.originalText().equals(SOURCE))
                .allMatch(entry -> entry.translatedText().isBlank());
    }

    @Test void aTieBetweenEquallyPlacedTranslationsIsOnlyProposed() throws Exception {
        Path mod = mod("rank2", "说明");
        var project = new LocalizationProjectService().createUsingBridgeForge(mod, "p", "P");
        String context = "data\\ships.csv#bf:csv:data/ships.csv#ship1:name";
        Path library = library(draft(SOURCE, "Advanced armor", context, TranslationProvenance.HUMAN_EDITED),
                draft(SOURCE, "Elite plating", context.replace("\\", "/"), TranslationProvenance.HUMAN_EDITED));
        var suggestions = new MemoryMatchSuggester(CandidateRanker.withDefaultRules()).suggest(project, library);
        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.applied()).isFalse();
            assertThat(suggestion.note()).contains("runner-up");
        });
    }

    @Test void fuzzyMatchesAreProposedWithAScoreButNeverAppliedByDefault() throws Exception {
        Path mod = mod("rank3", "增加60装甲值");
        var project = new LocalizationProjectService().createUsingBridgeForge(mod, "p", "P");
        Path library = library(draft("增加50装甲值", "Adds 50 armor",
                "data\\ships.csv#bf:csv:data/ships.csv#ship1:desc", TranslationProvenance.HUMAN_EDITED),
                draft(SOURCE, "ignored AI draft", "x#y", TranslationProvenance.AI_TRANSLATED));
        var suggester = new MemoryMatchSuggester(CandidateRanker.withDefaultRules());
        var suggestions = suggester.suggest(project, library);
        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.text()).isEqualTo("Adds 50 armor");
            assertThat(suggestion.exactSource()).isFalse();
            assertThat(suggestion.applied()).isFalse();
            assertThat(suggestion.note()).contains("fuzzy");
            assertThat(suggestion.score()).isBetween(0.4, 1.0);
        });
        assertThat(suggester.applyTo(project, suggestions).entries()).allMatch(entry -> entry.translatedText().isBlank());
    }

    @Test void ambiguousRecordPrefillIsRankedByNeighbouringUnitsAndOnlyTheClearWinnerIsApplied() throws Exception {
        Path mod = temporary.resolve("rank4");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"rank4\",\"name\":\"N\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/ships.csv"),
                "id,name\na,前面\nb,统帅\nc,后面\n");
        Path records = temporary.resolve("records/ships.json");
        Files.createDirectories(java.util.Objects.requireNonNull(records.getParent()));
        Files.writeString(records, "[{\"zh\":\"前面\",\"en\":\"Before\"},{\"zh\":\"统帅\",\"en\":\"Commander\"},"
                + "{\"zh\":\"后面\",\"en\":\"After\"},{\"zh\":\"其他\",\"en\":\"Other\"},"
                + "{\"zh\":\"统帅\",\"en\":\"Marshal\"},{\"zh\":\"别处\",\"en\":\"Elsewhere\"}]");
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        var result = new BridgeForgeTranslationPrefill().records(document, List.of(java.util.Objects.requireNonNull(records.getParent())));
        assertThat(result.path("ambiguous").asInt()).isEqualTo(1);
        assertThat(result.path("ranked_applied").asInt()).isEqualTo(1);
        var commander = java.util.stream.StreamSupport.stream(document.path("entries").spliterator(), false)
                .filter(entry -> entry.path("source").asText().equals("统帅")).findFirst().orElseThrow();
        assertThat(commander.path("translation").asText()).isEqualTo("Commander");
        assertThat(commander.path("provenance").asText()).isEqualTo("record-ranked");
        assertThat(commander.path("candidates_ranked").get(0).path("score").asDouble())
                .isGreaterThan(commander.path("candidates_ranked").get(1).path("score").asDouble());
    }
}
