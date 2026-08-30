package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationReportExporterTest {
    @TempDir Path temporary;

    @Test
    void writesStableOrderedEscapedReport() throws Exception {
        LocalizationProject project = new LocalizationProject(1, "mod", "patch", "Patch", List.of(
                new ProjectEntry(Path.of("data/b.csv"), "id", "A, \"quoted\"", "Translated",
                        TranslationProvenance.HUMAN_EDITED),
                new ProjectEntry(Path.of("data/a.csv"), "id", "Blank", "")));
        Path report = temporary.resolve("report.csv");

        new TranslationReportExporter().write(report, project);

        String text = Files.readString(report);
        assertThat(text).startsWith("source_file,key,status,provenance,source,translation\r\n")
                .contains("\"data/a.csv\",\"id\",\"UNTRANSLATED\"")
                .contains("\"A, \"\"quoted\"\"\"");
        assertThat(text.indexOf("data/a.csv")).isLessThan(text.indexOf("data/b.csv"));
    }
}
