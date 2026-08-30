package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorLocalizationDetectorTest {
    @Test
    void pairsOnlyExactFsfNamespaceSuffixAndKey() {
        ProjectEntry source = entry("aEP/data/names.csv", "csv:id=one:name", "源");
        ProjectEntry translated = entry(
                "aEP_En/data/names.csv", "csv:id=one:name", "Author Name");
        ProjectEntry unmatched = entry(
                "aEP_En/data/names.csv", "csv:id=two:name", "Only English");
        ProjectEntry unrelated = entry(
                "other_En/data/names.csv", "csv:id=one:name", "Not Explicit");

        AuthorLocalizationReport report = new AuthorLocalizationDetector()
                .detect(List.of(source, translated, unmatched, unrelated));

        assertThat(report.pairs()).containsExactly(
                new AuthorLocalizationPair(source, translated));
        assertThat(report.unmatched()).containsExactly(unmatched);
        assertThat(report.ambiguous()).isEmpty();
        assertThat(source.provenance()).isEqualTo(TranslationProvenance.MANUAL_IMPORT);
    }

    @Test
    void reportsMismatchedKeysInsteadOfGuessing() {
        AuthorLocalizationReport report = new AuthorLocalizationDetector().detect(List.of(
                entry("aEP/data/names.csv", "csv:id=one:name", "源"),
                entry("aEP_En/data/names.csv", "csv:id=other:name", "Wrong")));

        assertThat(report.pairs()).isEmpty();
        assertThat(report.unmatched()).hasSize(2);
    }

    private static ProjectEntry entry(String file, String key, String text) {
        return new ProjectEntry(Path.of(file), key, text, "");
    }
}
