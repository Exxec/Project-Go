package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.project.RootStringDiffService.Carry;
import com.ssmt.project.RootStringDiffService.Change;
import com.ssmt.project.RootStringDiffService.Unit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RootStringDiffServiceTest {
    private static final Path STRINGS = Path.of("data/strings/strings.json");

    @TempDir Path temporary;

    private Path root(String name, String json) throws Exception {
        Path root = Files.createDirectories(temporary.resolve(name));
        Path file = root.resolve(STRINGS);
        Files.createDirectories(java.util.Objects.requireNonNull(file.getParent()));
        Files.writeString(file, json);
        return root;
    }

    private static ProjectEntry packEntry(String pointer, String source, String translation) {
        return new ProjectEntry(STRINGS, "json:" + pointer, source, translation, TranslationProvenance.HUMAN_EDITED);
    }

    private static Unit unit(RootStringDiffService.Result result, String suffix) {
        return result.units().stream().filter(unit -> unit.key().endsWith(suffix)).findFirst().orElseThrow();
    }

    @Test
    void classifiesAddedRemovedChangedAndUnchangedFromTheStandardExtractors() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"same\":\"Alpha\",\"edit\":\"Beta\",\"gone\":\"Gamma\"}}");
        Path newRoot = root("new", "{\"g\":{\"same\":\"Alpha\",\"edit\":\"Beta two\",\"fresh\":\"Delta\"}}");

        var result = new RootStringDiffService().diff(oldRoot, newRoot, null);

        assertThat(unit(result, "/same").change()).isEqualTo(Change.UNCHANGED);
        assertThat(unit(result, "/edit").change()).isEqualTo(Change.CHANGED);
        assertThat(unit(result, "/gone").change()).isEqualTo(Change.REMOVED);
        assertThat(unit(result, "/fresh").change()).isEqualTo(Change.ADDED);
        assertThat(result.translationsSupplied()).isFalse();
        assertThat(result.units()).allMatch(unit -> unit.carry() == Carry.NOT_EVALUATED);
        assertThat(unit(result, "/fresh").oldDigest()).isEmpty();
        assertThat(unit(result, "/gone").newDigest()).isEmpty();
    }

    @Test
    void aFileThatCannotBeReadOnOneSideIsListedAndLeftOutInsteadOfFailingTheRun() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"same\":\"Alpha\"}}");
        Path newRoot = root("new", "{\"g\":{\"same\":\"Alpha\"}");
        Path tips = newRoot.resolve("data/strings/tips.json");
        Files.writeString(tips, "[\"Fine\"]");
        Files.writeString(oldRoot.resolve("data/strings/tips.json"), "[\"Fine\"]");

        var result = new RootStringDiffService().diff(oldRoot, newRoot, null);

        assertThat(result.unreadableFiles()).containsExactly("data/strings/strings.json");
        assertThat(result.units()).extracting(Unit::file).containsOnly("data/strings/tips.json");
        assertThat(result.count(Change.UNCHANGED)).isEqualTo(1);
        assertThat(RootStringDiffService.toJson(result).path("unreadableFiles")).hasSize(1);
    }

    @Test
    void packTranslationsCarryOverGoStaleOrAreMissing() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"same\":\"Alpha\",\"edit\":\"Beta\",\"gone\":\"Gamma\",\"bare\":\"Eps\"}}");
        Path newRoot = root("new", "{\"g\":{\"same\":\"Alpha\",\"edit\":\"Beta two\",\"fresh\":\"Delta\",\"bare\":\"Eps\"}}");
        var pack = new LocalizationProject(1, "vanilla", "vanilla.tr", "Vanilla", List.of(
                packEntry("/g/same", "Alpha", "A"), packEntry("/g/edit", "Beta", "B"),
                packEntry("/g/gone", "Gamma", "G"), packEntry("/g/bare", "Eps", " "),
                packEntry("/g/other", "Unrelated", "U")));

        var result = new RootStringDiffService().diff(oldRoot, newRoot,
                new RootStringDiffService.Translations().withProject(pack));

        assertThat(unit(result, "/same").carry()).isEqualTo(Carry.CARRIED_OVER);
        assertThat(unit(result, "/edit").carry()).isEqualTo(Carry.STALE);
        assertThat(unit(result, "/gone").carry()).isEqualTo(Carry.ORPHANED);
        assertThat(unit(result, "/fresh").carry()).isEqualTo(Carry.NEEDS_TRANSLATION);
        assertThat(unit(result, "/bare").carry()).isEqualTo(Carry.NEEDS_TRANSLATION);
        assertThat(result.count(Carry.CARRIED_OVER)).isEqualTo(1);
    }

    @Test
    void aPackEntryWrittenForDifferentOldTextDoesNotCarryOver() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"same\":\"Alpha\"}}");
        Path newRoot = root("new", "{\"g\":{\"same\":\"Alpha\"}}");
        var pack = new LocalizationProject(1, "vanilla", "vanilla.tr", "Vanilla",
                List.of(packEntry("/g/same", "Older wording", "A")));

        var result = new RootStringDiffService().diff(oldRoot, newRoot,
                new RootStringDiffService.Translations().withProject(pack));

        assertThat(unit(result, "/same").carry()).isEqualTo(Carry.NEEDS_TRANSLATION);
        assertThat(result.packEntriesForOtherText()).isEqualTo(1);
    }

    @Test
    void glossaryTermMatchesAWholeOldString() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"same\":\"Alpha\",\"edit\":\"Beta\"}}");
        Path newRoot = root("new", "{\"g\":{\"same\":\"Alpha\",\"edit\":\"Beta two\"}}");
        var glossary = new GlossaryDocument(1, "en", "de", List.of(
                new GlossaryTerm("Alpha", "A", ""), new GlossaryTerm("Beta", "B", "")));

        var result = new RootStringDiffService().diff(oldRoot, newRoot,
                new RootStringDiffService.Translations().withGlossary(glossary));

        assertThat(unit(result, "/same").carry()).isEqualTo(Carry.CARRIED_OVER);
        assertThat(unit(result, "/edit").carry()).isEqualTo(Carry.STALE);
    }

    @Test
    void reportAndSummaryHoldNoSourceOrTranslatedText() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"same\":\"Secret phrase\"}}");
        Path newRoot = root("new", "{\"g\":{\"same\":\"Secret phrase\",\"fresh\":\"Another secret\"}}");
        var pack = new LocalizationProject(1, "vanilla", "vanilla.tr", "Vanilla",
                List.of(packEntry("/g/same", "Secret phrase", "Hidden translation")));

        var result = new RootStringDiffService().diff(oldRoot, newRoot,
                new RootStringDiffService.Translations().withProject(pack));
        JsonNode json = RootStringDiffService.toJson(result);
        String everything = json + RootStringDiffService.summary(result);

        assertThat(json.path("counts").path("ADDED").asInt()).isEqualTo(1);
        assertThat(json.path("carryCounts").path("CARRIED_OVER").asInt()).isEqualTo(1);
        assertThat(everything).doesNotContain("Secret", "secret", "Hidden", oldRoot.toString());
        assertThat(RootStringDiffService.toJson(result)).isEqualTo(json);
    }
}
