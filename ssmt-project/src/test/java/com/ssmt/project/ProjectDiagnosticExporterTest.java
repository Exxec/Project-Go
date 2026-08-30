package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDiagnosticExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void omitsTranslationContentAndRedactsPathsAndSecrets() throws Exception {
        LocalizationProject project = new LocalizationProject(
                1,
                "example",
                "example.fr",
                "Example French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Private source",
                        "Private translation")));
        Path output = temporaryDirectory.resolve("diagnostic.json");

        new ProjectDiagnosticExporter().write(
                output,
                project,
                "api_key=abcdef C:\\Users\\person\\source");

        String json = Files.readString(output);
        assertThat(json)
                .contains("[REDACTED]")
                .doesNotContain(
                        "abcdef",
                        "person",
                        "Private source",
                        "Private translation");
    }
}
