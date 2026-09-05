package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.extractor.csv.OptInCsvFileSchema;
import com.ssmt.extractor.csv.OptInCsvSchema;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvGapSchemaSuggesterTest {

    private static final Path SPECIAL_ITEMS = Path.of("data/hulls/special_items.csv");
    private static final Path EXTRA = Path.of("data/hulls/extra.csv");

    private final CsvGapSchemaSuggester suggester = new CsvGapSchemaSuggester();

    @Test
    void suggestsSchemaForUnrecognizedCsvWithIdAndNonAsciiText(@TempDir Path modRoot)
            throws Exception {
        writeFixtureMod(modRoot);

        GapSchemaSuggestion suggestion = suggestOne(modRoot, SPECIAL_ITEMS);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        OptInCsvFileSchema schema = suggestion.schema().orElseThrow();
        assertThat(schema.path()).isEqualTo(SPECIAL_ITEMS);
        assertThat(schema.identityColumns()).containsExactly("id");
        assertThat(schema.textColumns()).containsExactly("name", "desc");
        assertThat(suggestion.nonAsciiCellCount()).isEqualTo(4);
        assertThat(suggestion.reason()).isNotBlank();
    }

    @Test
    void prefersHeaderNamedIdCaseInsensitively(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "code,ID,name\nA,1,圣物\nB,2,圣水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().identityColumns()).containsExactly("ID");
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("name");
    }

    @Test
    void fallsBackToFirstUniqueNonBlankColumn(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "code,label,note\nA,圣物,first\nB,圣水,x\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().identityColumns()).containsExactly("code");
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("label");
    }

    @Test
    void reportsNoIdColumnWhenNoColumnIsUniqueAndNonBlank(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "code,name\nA,圣物\nA,圣水\n,圣水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.NO_ID_COLUMN);
        assertThat(suggestion.schema()).isEmpty();
        assertThat(suggestion.nonAsciiCellCount()).isEqualTo(3);
    }

    @Test
    void fallsBackToOtherColumnWhenIdHeaderHasDuplicateValues(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"),
                "id,code,name\ndup,A,圣物\ndup,B,圣水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().identityColumns()).containsExactly("code");
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("name");
    }

    @Test
    void reportsNoIdColumnWhenIdDuplicatesAndNoOtherColumnIsUnique(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "id,name\ndup,圣物\ndup,圣物\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.NO_ID_COLUMN);
        assertThat(suggestion.schema()).isEmpty();
    }

    @Test
    void fallsBackToOtherColumnWhenIdHeaderHasBlankValue(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"),
                "id,code,name\n,A,圣物\nrelic,B,圣水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().identityColumns()).containsExactly("code");
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("name");
    }

    @Test
    void reportsNoIdColumnWhenIdBlankAndNoOtherColumnIsUnique(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "id,name\n,圣物\ndup,圣物\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.NO_ID_COLUMN);
        assertThat(suggestion.schema()).isEmpty();
    }

    @Test
    void countsSingleNonAsciiCharacterCellsAsTextColumns(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "id,name\nrelic,圣\nwater,水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().identityColumns()).containsExactly("id");
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("name");
        assertThat(suggestion.nonAsciiCellCount()).isEqualTo(2);
    }

    @Test
    void reportsNoTextColumnsWhenAllCellsAreAscii(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/config/mechanics.csv"), "id,value\nplaceholder,42\n");

        GapSchemaSuggestion suggestion =
                suggestOne(modRoot, Path.of("data/config/mechanics.csv"));

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.NO_TEXT_COLUMNS);
        assertThat(suggestion.schema()).isEmpty();
        assertThat(suggestion.nonAsciiCellCount()).isZero();
    }

    @Test
    void ignoresHashCommentAndBlankRowsDuringInference(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"),
                "id,name\n#舰船注释行：结构行\nrelic,圣物\n\nwater,圣水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().identityColumns()).containsExactly("id");
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("name");
        assertThat(suggestion.nonAsciiCellCount()).isEqualTo(2);
    }

    @Test
    void reportsNoTextColumnsWhenOnlyCommentRowsHoldNonAscii(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "id,name\n#舰船注释行：结构行\nrelic,Relic\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.NO_TEXT_COLUMNS);
        assertThat(suggestion.schema()).isEmpty();
    }

    @Test
    void reportsUnparseableForMalformedCsv(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/extra.csv"), "id,name,name\nrelic,圣物,圣水\n");

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.UNPARSEABLE);
        assertThat(suggestion.schema()).isEmpty();
        assertThat(suggestion.reason()).contains("CSV");
    }

    @Test
    void ignoresNonCsvFindings(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/legacy.ship"), "{\"hullName\": \"圣物\"}\n");

        List<GapSchemaSuggestion> suggestions = suggester.suggest(modRoot, List.of(
                new CoverageGapFinding(Path.of("data/hulls/legacy.ship"), "圣物")));

        assertThat(suggestions).isEmpty();
    }

    @Test
    void decodesGb18030FallbackContent(@TempDir Path modRoot) throws Exception {
        Path file = modRoot.resolve("data/hulls/extra.csv");
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.write(file, "id,name\nrelic,圣物\n".getBytes(Charset.forName("GB18030")));

        GapSchemaSuggestion suggestion = suggestOne(modRoot, EXTRA);

        assertThat(suggestion.status()).isEqualTo(GapSchemaStatus.SUGGESTED);
        assertThat(suggestion.schema().orElseThrow().textColumns()).containsExactly("name");
    }

    @Test
    void toCatalogEmitsOnlySuggestedEntries(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/good.csv"), "id,name\nrelic,圣物\n");
        write(modRoot.resolve("data/hulls/ascii.csv"), "id,name\nrelic,Relic\n");

        List<GapSchemaSuggestion> suggestions = suggester.suggest(modRoot, List.of(
                new CoverageGapFinding(Path.of("data/hulls/good.csv"), "圣物"),
                new CoverageGapFinding(Path.of("data/hulls/ascii.csv"), "圣物")));

        OptInCsvSchema catalog = CsvGapSchemaSuggester.toCatalog(suggestions);

        assertThat(catalog.schemaVersion()).isEqualTo(OptInCsvSchema.CURRENT_SCHEMA_VERSION);
        assertThat(catalog.files())
                .extracting(OptInCsvFileSchema::path)
                .containsExactly(Path.of("data/hulls/good.csv"));
    }

    @Test
    void mergeIntoSkipsExistingPathsAndKeepsGuards(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/good.csv"), "id,name\nrelic,圣物\n");
        write(modRoot.resolve("data/hulls/other.csv"), "id,name\nwater,圣水\n");
        List<GapSchemaSuggestion> suggestions = suggester.suggest(modRoot, List.of(
                new CoverageGapFinding(Path.of("data/hulls/good.csv"), "圣物"),
                new CoverageGapFinding(Path.of("data/hulls/other.csv"), "圣水")));
        OptInCsvSchema existing = new OptInCsvSchema(
                OptInCsvSchema.CURRENT_SCHEMA_VERSION,
                List.of(new OptInCsvFileSchema(
                        Path.of("data/hulls/good.csv"), List.of("id"), List.of("name"))));

        OptInCsvSchema merged = CsvGapSchemaSuggester.mergeInto(existing, suggestions);

        assertThat(merged.files())
                .extracting(OptInCsvFileSchema::path)
                .containsExactly(
                        Path.of("data/hulls/good.csv"), Path.of("data/hulls/other.csv"));

        Path standard = Path.of("data/strings/descriptions.csv");
        GapSchemaSuggestion overlap = new GapSchemaSuggestion(
                standard,
                GapSchemaStatus.SUGGESTED,
                Optional.of(new OptInCsvFileSchema(standard, List.of("id"), List.of("text1"))),
                "圣物",
                1);

        assertThatThrownBy(() -> CsvGapSchemaSuggester.mergeInto(existing, List.of(overlap)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standard handler");
    }

    private GapSchemaSuggestion suggestOne(Path modRoot, Path relative) throws Exception {
        List<GapSchemaSuggestion> suggestions = suggester.suggest(modRoot, List.of(
                new CoverageGapFinding(relative, "圣物")));
        assertThat(suggestions).hasSize(1);
        return suggestions.getFirst();
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void writeFixtureMod(Path modRoot) throws Exception {
        try (InputStream stream = Objects.requireNonNull(CsvGapSchemaSuggesterTest.class
                .getResourceAsStream("/fixtures/mod/data/hulls/special_items.csv"))) {
            Path target = modRoot.resolve(SPECIAL_ITEMS);
            Files.createDirectories(Objects.requireNonNull(target.getParent()));
            Files.write(target, stream.readAllBytes());
        }
    }
}
