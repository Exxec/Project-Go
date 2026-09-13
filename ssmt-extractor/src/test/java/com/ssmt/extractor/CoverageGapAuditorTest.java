package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageGapAuditorTest {

    @Test
    void reportsNonAsciiTextInUnselectedStandardColumnsWithoutSelectingOrSuggestingThem(
            @TempDir Path modRoot) throws Exception {
        Path relative = Path.of("data/weapons/weapon_data.csv");
        write(modRoot.resolve(relative), "id,name,groupTag,tags,damage/shot\n"
                + "test,Test,æŠ€æœ¯åˆ†ç»„,æ ‡ç­¾,123\n");
        ExtractionReport report = new ExtractionReport(List.of(), List.of(), List.of(
                new FileCoverage(relative, "standard", "EXTRACTED", 1, "SELECTED_STRINGS_ONLY")));

        List<StandardCsvGapAuditor.Finding> findings =
                new StandardCsvGapAuditor().audit(modRoot, report);

        assertThat(findings).extracting(StandardCsvGapAuditor.Finding::column)
                .containsExactly("groupTag", "tags");
        assertThat(findings).extracting(StandardCsvGapAuditor.Finding::status)
                .containsOnly("UNSELECTED_COLUMN_WITH_NON_ASCII_TEXT");
        assertThat(new CsvGapSchemaSuggester().suggest(modRoot,
                new CoverageGapAuditor().audit(modRoot, report))).isEmpty();
        assertThat(Files.readString(modRoot.resolve(relative), StandardCharsets.UTF_8))
                .contains("æŠ€æœ¯åˆ†ç»„", "æ ‡ç­¾");
    }

    @Test
    void suggestsAsciiTextWithoutIncludingTechnicalColumns(@TempDir Path modRoot) throws Exception {
        Path relative = Path.of("data/custom/augments.csv");
        write(modRoot.resolve(relative),
                "augmentID,name,description,script\nreactor,Reactor,More flux.,example.Script\n");
        var findings = new CoverageGapAuditor().audit(modRoot,
                new ExtractionReport(List.of(), List.of(relative)));
        assertThat(findings).hasSize(1);
        var suggested = new CsvGapSchemaSuggester().suggest(modRoot, findings).getFirst();
        assertThat(suggested.schema().orElseThrow().textColumns())
                .containsExactly("name", "description");
        assertThat(suggested.schema().orElseThrow().identityColumns()).containsExactly("augmentID");
    }

    @Test
    void flagsUnrecognizedCsvFileContainingNonAsciiText(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hullmods/hull_mods.csv"),
                "name,id\n偏光耗散镀层,AeCoat\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/hullmods/hull_mods.csv")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings)
                .extracting(CoverageGapFinding::relativeSourceFile)
                .containsExactly(Path.of("data/hullmods/hull_mods.csv"));
        assertThat(findings.getFirst().sample()).contains("偏光耗散镀层");
    }

    @Test
    void flagsCsvWithSingleNonAsciiCharacter(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hullmods/single.csv"),
                "name,id\n圣,AeCoat\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/hullmods/single.csv")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings)
                .extracting(CoverageGapFinding::relativeSourceFile)
                .containsExactly(Path.of("data/hullmods/single.csv"));
        assertThat(findings.getFirst().sample()).contains("圣");
    }

    @Test
    void flagsCsvWithOneNonAsciiCharacterBetweenAsciiCharacters(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("data/hullmods/mixed.csv"),
                "name,id\nA圣B,AeCoat\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/hullmods/mixed.csv")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings)
                .extracting(CoverageGapFinding::relativeSourceFile)
                .containsExactly(Path.of("data/hullmods/mixed.csv"));
        assertThat(findings.getFirst().sample()).contains("A圣B");
    }

    @Test
    void ignoresSkippedCsvFilesWithOnlyAsciiContent(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/config/mechanics_only.csv"),
                "id,value\nplaceholder,42\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/config/mechanics_only.csv")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings).isEmpty();
    }

    @Test
    void flagsUnrecognizedShipFileContainingNonAsciiText(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/legacy.ship"),
                "{\n  \"hullName\": \"偏光耗散镀层\",\n}\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/hulls/legacy.ship")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings)
                .extracting(CoverageGapFinding::relativeSourceFile)
                .containsExactly(Path.of("data/hulls/legacy.ship"));
        assertThat(findings.getFirst().sample()).contains("偏光耗散镀层");
    }

    @Test
    void ignoresSkippedShipFilesWithOnlyAsciiContent(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/hulls/plain.ship"),
                "{\"hullName\": \"Plain Hull\"}\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/hulls/plain.ship")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings).isEmpty();
    }

    @Test
    void ignoresSkippedNonCsvFilesRegardlessOfContent(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("graphics/icons/note.txt"), "偏光耗散镀层");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("graphics/icons/note.txt")));

        List<CoverageGapFinding> findings = new CoverageGapAuditor().audit(modRoot, report);

        assertThat(findings).isEmpty();
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
