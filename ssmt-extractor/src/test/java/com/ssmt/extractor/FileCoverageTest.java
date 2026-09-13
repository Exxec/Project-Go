package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCoverageTest {
    @TempDir Path directory;

    @Test void unsupportedSupportedEmptyAndExtractedFilesAreDistinctAndReadOnly() throws Exception {
        Path strings = Files.createDirectories(directory.resolve("data/strings"));
        Path weapons = Files.createDirectories(directory.resolve("data/weapons"));
        Files.writeString(strings.resolve("descriptions.csv"), "id,type,text1\n");
        Files.writeString(weapons.resolve("weapon_data.csv"), "id,name\na,Hello\n");
        Files.writeString(directory.resolve("unrecognized.txt"), "Unselected narrative");
        var coordinator = new ExtractionCoordinator(List.of(new StandardCsvFileExtractor()));
        var report = coordinator.extractMod("example", directory);
        assertThat(report.fileCoverage()).hasSize(3);
        assertThat(report.fileCoverage()).extracting(FileCoverage::status)
                .containsExactly("SUPPORTED_NO_STRINGS", "EXTRACTED", "UNSUPPORTED");
        assertThat(report.fileCoverage()).extracting(FileCoverage::extractedStrings)
                .containsExactly(0, 1, 0);
        assertThat(report).isEqualTo(coordinator.extractMod("example", directory));
        assertThat(Files.readString(directory.resolve("unrecognized.txt")))
                .isEqualTo("Unselected narrative");
        assertThat(report.fileCoverage().getFirst().reason())
                .isEqualTo("NO_STRINGS_SELECTED_BY_HANDLER");
    }

    @Test void legacyReportsDoNotInventObservedCoverage() {
        assertThat(new ExtractionReport(List.of(), List.of()).fileCoverage()).isEmpty();
    }
}
