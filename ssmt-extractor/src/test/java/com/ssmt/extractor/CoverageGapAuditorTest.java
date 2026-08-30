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
    void ignoresSkippedCsvFilesWithOnlyAsciiContent(@TempDir Path modRoot) throws Exception {
        write(modRoot.resolve("data/config/mechanics_only.csv"),
                "id,value\nplaceholder,42\n");
        ExtractionReport report = new ExtractionReport(
                List.of(), List.of(Path.of("data/config/mechanics_only.csv")));

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
