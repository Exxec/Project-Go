package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.extractor.CoverageGapAuditor;
import com.ssmt.extractor.CoverageGapFinding;
import com.ssmt.extractor.CsvGapSchemaSuggester;
import com.ssmt.extractor.ExtractionReport;
import com.ssmt.extractor.GapSchemaStatus;
import com.ssmt.extractor.GapSchemaSuggestion;
import com.ssmt.extractor.csv.ConfiguredCsvFileExtractor;
import com.ssmt.extractor.csv.OptInCsvSchema;
import com.ssmt.patcher.PatchArtifact;
import com.ssmt.patcher.StandardFileInjector;
import com.ssmt.patcher.TranslationReplacement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the whole gap-coverage loop on one fixture: an advisory finding becomes a
 * reviewable draft catalog, that catalog extracts through the ordinary opt-in CSV path,
 * and the resulting stable key reinjects without disturbing structural rows. Lives here
 * because this is the first module whose test classpath sees both the extractor and the
 * patcher, which do not depend on each other.
 */
class GapCoverageRoundTripTest {

    private static final Path SPECIAL_ITEMS = Path.of("data/hulls/special_items.csv");
    private static final String NAME_KEY = "csv:id=church_relic:name";
    private static final String SOURCE_CSV = """
            id,name,desc,cost
            #舰船特殊物品表：结构注释行
            church_relic,圣物,古老的教会圣物,500
            holy_water,圣水,经过祝福的清水,250
            """;

    @Test
    void suggestedCatalogExtractsAndReinjectsWithoutTouchingStructuralRows(
            @TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve(SPECIAL_ITEMS);
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, SOURCE_CSV, StandardCharsets.UTF_8);

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(
                modRoot, new ExtractionReport(List.of(), List.of(SPECIAL_ITEMS)));
        assertThat(findings).hasSize(1);

        List<GapSchemaSuggestion> suggestions =
                new CsvGapSchemaSuggester().suggest(modRoot, findings);
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().status()).isEqualTo(GapSchemaStatus.SUGGESTED);

        OptInCsvSchema catalog = CsvGapSchemaSuggester.toCatalog(suggestions);
        List<ExtractedString> extracted = new ConfiguredCsvFileExtractor(catalog).extract(
                new ExtractionRequest("gap_mod", modRoot, source));

        assertThat(extracted)
                .extracting(ExtractedString::key)
                .contains(NAME_KEY, "csv:id=church_relic:desc", "csv:id=holy_water:name");
        ExtractedString target = extracted.stream()
                .filter(item -> NAME_KEY.equals(item.key()))
                .findFirst()
                .orElseThrow();
        assertThat(target.sourceFile()).isEqualTo(SPECIAL_ITEMS);
        assertThat(target.originalText()).isEqualTo("圣物");

        PatchArtifact artifact = new StandardFileInjector().inject(modRoot, List.of(
                new TranslationReplacement(
                        SPECIAL_ITEMS, target.key(), target.originalText(), "Church Relic")));

        String injected = new String(artifact.content(), StandardCharsets.UTF_8);
        assertThat(injected).contains("church_relic,Church Relic,古老的教会圣物,500");
        assertThat(injected).contains("#舰船特殊物品表：结构注释行");
        assertThat(injected).contains("holy_water,圣水,经过祝福的清水,250");
        assertThat(injected).doesNotContain(",圣物,");
    }
}
