package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerminologyConsistencyAuditorTest {
    private final TerminologyConsistencyAuditor auditor =
            new TerminologyConsistencyAuditor();

    @Test
    void reportsExactRepeatedSourceWithDifferentTranslations() {
        LocalizationProject project = project(
                entry("a.csv", "csv:id=1:name", "Term", "First"),
                entry("b.json", "/name", "Term", "Second"));

        assertThat(auditor.audit(project))
                .extracting(ConsistencyFinding::type)
                .containsExactly(ConsistencyFindingType.EXACT_DUPLICATE_SOURCE);
    }

    @Test
    void reportsNormalizedTermOnlyInEquivalentContexts() {
        LocalizationProject equivalent = project(
                entry("a.csv", "csv:id=1:name", "Warm Pool", "Warm Pool"),
                entry("b.csv", "csv:id=2:name", "warm-pool", "Thermal Pool"));
        LocalizationProject different = project(
                entry("a.csv", "csv:id=1:name", "Warm Pool", "Warm Pool"),
                entry("b.json", "/description", "warm-pool", "Thermal Pool"));

        assertThat(auditor.audit(equivalent))
                .extracting(ConsistencyFinding::type)
                .containsExactly(ConsistencyFindingType.NORMALIZED_TERM);
        assertThat(auditor.audit(different)).isEmpty();
    }

    @Test
    void ignoresConsistentAndBlankTranslations() {
        LocalizationProject project = project(
                entry("a.csv", "csv:id=1:name", "Term", "Same"),
                entry("b.csv", "csv:id=2:name", "Term", "Same"),
                entry("c.csv", "csv:id=3:name", "Term", ""));

        assertThat(auditor.audit(project)).isEmpty();
    }

    @Test
    void countsUntranslatedExactSourcesWithoutMergingNormalizedVariants() {
        LocalizationProject project = project(
                entry("a.csv", "name:1", "Warm Pool", ""),
                entry("b.csv", "name:2", "Warm Pool", "Thermal Pool"),
                entry("c.csv", "name:3", "warm-pool", ""),
                entry("d.csv", "name:4", "   ", ""));
        var counts = auditor.sourceOccurrences(project);

        assertThat(counts).containsEntry("Warm Pool", 2).containsEntry("warm-pool", 1)
                .hasSize(2);
        assertThat(counts.keySet()).containsExactly("Warm Pool", "warm-pool");
        assertThatThrownBy(() -> counts.put("mutated", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(project.entries().getFirst().translatedText()).isEmpty();
    }

    private static LocalizationProject project(ProjectEntry... entries) {
        return new LocalizationProject(1, "mod", "patch", "Patch", List.of(entries));
    }

    private static ProjectEntry entry(
            String file, String key, String source, String translated) {
        return new ProjectEntry(Path.of(file), key, source, translated);
    }
}
