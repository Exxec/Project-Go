package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiCoverageSummaryTest {
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper();

    private LocalizationProject project() {
        return new LocalizationProject(1, "example", "example.en", "Example English", List.of(
                new ProjectEntry(Path.of("data/strings/strings.json"), "json:/a", "Hello", ""),
                new ProjectEntry(Path.of("data/strings/strings.json"), "json:/b", "Goodbye", "")));
    }

    @Test void subsetExportReportsSelectedScopeAndNeverInventsFullCoverage() throws Exception {
        var project = project();
        var service = new AiTranslationExchangeService();
        Path file = root.resolve("response.json");
        service.exportPackage(file, project, List.of(project.entries().getFirst()), "Example", "en", "fr");
        ObjectNode response = (ObjectNode) json.readTree(file.toFile());
        assertThat(response.path("coverageSummary").path("scope").asText())
                .isEqualTo("SELECTED_PROJECT_ENTRIES_ONLY");
        assertThat(response.path("coverageSummary").path("projectEntryCount").asInt()).isEqualTo(2);
        assertThat(response.path("coverageSummary").path("exportedEntryCount").asInt()).isEqualTo(1);
        assertThat(response.path("coverageSummary").path("fullModCoverage").asText()).isEqualTo("NOT_ESTABLISHED");
        assertThat(response.path("instructions").asText()).contains("does not establish complete mod coverage");
        ((ObjectNode) response.path("entries").get(0)).put("translation", "Bonjour");
        json.writeValue(file.toFile(), response);
        assertThat(service.importResponse(file, project, null).importedEntries()).isEqualTo(1);
        assertThat(project.entries()).allMatch(entry -> entry.translatedText().isBlank());
    }

    @Test void rejectsInventedFullCoverageAndCountChangesButReadsLegacyPackages() throws Exception {
        var project = project();
        var service = new AiTranslationExchangeService();
        Path file = root.resolve("response.json");
        service.exportPackage(file, project, List.of(project.entries().getFirst()), "Example", "en", "fr");
        ObjectNode response = (ObjectNode) json.readTree(file.toFile());
        ((ObjectNode) response.path("entries").get(0)).put("translation", "Bonjour");
        ObjectNode coverage = (ObjectNode) response.path("coverageSummary");
        coverage.put("fullModCoverage", "COMPLETE");
        json.writeValue(file.toFile(), response);
        assertThatThrownBy(() -> service.importResponse(file, project, null))
                .isInstanceOf(ProjectException.class).hasMessageContaining("coverage metadata");
        coverage.put("fullModCoverage", "NOT_ESTABLISHED");
        coverage.put("exportedEntryCount", 2);
        json.writeValue(file.toFile(), response);
        assertThatThrownBy(() -> service.importResponse(file, project, null))
                .isInstanceOf(ProjectException.class).hasMessageContaining("coverage metadata");
        response.remove("coverageSummary");
        json.writeValue(file.toFile(), response);
        assertThat(service.importResponse(file, project, null).importedEntries()).isEqualTo(1);
    }
}
