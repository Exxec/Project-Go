package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationCoverageAuditorTest {
    private final TranslationCoverageAuditor auditor = new TranslationCoverageAuditor();

    @Test
    void computesOverallAndPerFileCoverage() {
        LocalizationProject project = project(
                entry("a.csv", "csv:id=1:name", "One", "Translated"),
                entry("a.csv", "csv:id=2:name", "Two", ""),
                entry("b.json", "/name", "Three", "Translated"));

        TranslationCoverageReport report = auditor.audit(project);

        assertThat(report.totalEntries()).isEqualTo(3);
        assertThat(report.translatedEntries()).isEqualTo(2);
        assertThat(report.translatedFraction()).isCloseTo(2.0 / 3.0, offset(0.0001));
        assertThat(report.files()).extracting(FileTranslationCoverage::sourceFile)
                .containsExactly(Path.of("a.csv"), Path.of("b.json"));
        assertThat(report.files()).filteredOn(file -> file.sourceFile().equals(Path.of("a.csv")))
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.totalEntries()).isEqualTo(2);
                    assertThat(file.translatedEntries()).isEqualTo(1);
                    assertThat(file.translatedFraction()).isCloseTo(0.5, offset(0.0001));
                });
    }

    @Test
    void reportsFullCoverageForAnEmptyProject() {
        TranslationCoverageReport report = auditor.audit(project());

        assertThat(report.totalEntries()).isZero();
        assertThat(report.translatedEntries()).isZero();
        assertThat(report.translatedFraction()).isEqualTo(1.0);
        assertThat(report.files()).isEmpty();
    }

    private static LocalizationProject project(ProjectEntry... entries) {
        return new LocalizationProject(1, "mod", "patch", "Patch", List.of(entries));
    }

    private static ProjectEntry entry(
            String file, String key, String source, String translated) {
        return new ProjectEntry(Path.of(file), key, source, translated);
    }
}
