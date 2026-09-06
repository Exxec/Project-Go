package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectEntry;
import com.ssmt.project.ReconciliationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real reported export, earlier AI response, and the mod files needed by extraction. */
class EdmundCorpusWorkflowTest {
    @TempDir Path directory;

    @Test
    void actual187EntryResponseSurvivesRestartAnd207EntryExport() throws Exception {
        verify(false);
    }

    @Test
    void changedSourceRequiresReviewAndDoesNotInheritOldTranslation() throws Exception {
        verify(true);
    }

    private void verify(boolean changedSource) throws Exception {
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/edmund/workflow.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path target = directory.resolve(entry.getName().replace('\\', '/')).normalize();
                if (!target.startsWith(directory)) { throw new IllegalArgumentException("Unsafe fixture path"); }
                if (!entry.getName().replace('\\', '/').endsWith("/")) {
                    Files.createDirectories(java.util.Objects.requireNonNull(target.getParent()));
                    Files.copy(zip, target);
                }
            }
        }
        var json = new ObjectMapper();
        var originalExport = json.readTree(directory.resolve("export-207.json").toFile());
        assertThat(originalExport.path("entries").size()).isEqualTo(207);
        originalExport.path("entries").forEach(e ->
                assertThat(e.path("existingTranslation").asText()).isEmpty());
        var response = json.readTree(directory.resolve("translated-187.json").toFile());
        var oldEntries = new ArrayList<ProjectEntry>();
        for (var item : response.path("entries")) {
            oldEntries.add(new ProjectEntry(Path.of(item.path("relativeFilePath").asText()),
                    item.path("internalId").asText(), item.path("source").asText(), ""));
        }
        assertThat(oldEntries).hasSize(187);
        Path source = directory.resolve("source");
        String name = response.path("originalModName").asText();
        Path folder = SsmtApplication.artifactDirectory(source, null, name);
        Files.createDirectories(folder);
        Path projectFile = folder.resolve(SsmtApplication.artifactBaseName(name) + " project.ssmt.json");
        var service = new LocalizationProjectService();
        service.write(projectFile, new LocalizationProject(1, "a16709513_wkt",
                "a16709513_wkt.translation", "Church English", oldEntries));
        var workspace = new ProjectWorkspaceController(new TranslationEditorController());
        workspace.open(source, projectFile);
        workspace.importAiResponse(directory.resolve("translated-187.json"), null);
        assertThat(service.read(projectFile).entries()).allMatch(e -> !e.translatedText().isBlank());

        if (changedSource) {
            Path file = source.resolve("data/config/exerelinFactionConfig/aDM_jaohe.json");
            String text = Files.readString(file);
            assertThat(text).contains("Splinter Mercenaries");
            Files.writeString(file, text.replace("Splinter Mercenaries", "Changed mercenaries"));
            var preview = workspace.previewRefresh();
            assertThat(preview.report().count(ReconciliationStatus.CHANGED)).isEqualTo(1);
            assertThat(preview.report().count(ReconciliationStatus.ADDED)).isEqualTo(20);
            assertThat(preview.report().entries().stream()
                    .filter(e -> e.status() == ReconciliationStatus.CHANGED).findFirst().orElseThrow()
                    .previousTranslation()).isEqualTo("Splinter Mercenaries");
            workspace.create(source, projectFile, "unused", "unused");
            assertThat(workspace.lastRefreshResult().orElseThrow().report()
                    .count(ReconciliationStatus.CHANGED)).isEqualTo(1);
        } else {
            // Cold Start New Translation is the GUI route that formerly overwrote the project.
            workspace = new ProjectWorkspaceController(new TranslationEditorController());
            workspace.create(source, projectFile, "unused", "unused");
        }
        Path exported = folder.resolve("recovered-207.json");
        workspace.exportAiPackage(exported, name, "zh", "en", 250);
        var result = json.readTree(exported.toFile()).path("entries");
        assertThat(result.size()).isEqualTo(207);
        int preserved = 0;
        for (var entry : result) {
            String id = entry.path("id").asText();
            var previous = java.util.stream.StreamSupport.stream(response.path("entries").spliterator(), false)
                    .filter(e -> e.path("id").asText().equals(id)).findFirst();
            boolean unchanged = previous.isPresent()
                    && previous.get().path("source").asText().equals(entry.path("source").asText());
            String expected = unchanged ? previous.get().path("translation").asText() : "";
            assertThat(entry.path("existingTranslation").asText()).as(id).isEqualTo(expected);
            assertThat(entry.path("translation").asText()).as(id).isEqualTo(expected);
            assertThat(originalExport.path("entries").findValuesAsText("id")).contains(id);
            if (!expected.isEmpty()) { preserved++; }
        }
        assertThat(preserved).isEqualTo(changedSource ? 186 : 187);
        assertThat(service.read(projectFile).entries().stream().filter(e -> !e.translatedText().isBlank()))
                .hasSize(preserved);
    }
}
