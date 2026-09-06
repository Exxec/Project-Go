package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.LocalizationProjectService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the real controller/service chain invoked by Start New Translation, then AI Export. */
class ExistingProjectAiExportWorkflowTest {
    @TempDir
    Path directory;

    @Test
    void restartingTranslationRefreshes187SavedTranslationsBeforeExporting207Entries() throws Exception {
        verifyWorkflow(false, false);
    }

    @Test
    void restartingOpenProjectKeepsUnsavedEditorTranslations() throws Exception {
        verifyWorkflow(true, false);
    }

    @Test
    void explicitRefreshAndExportPreserveTheSameInvariant() throws Exception {
        verifyWorkflow(false, true);
    }

    @Test
    void startingWithAdditionalCsvCoverageRefreshesTheExistingProject() throws Exception {
        verifyWorkflow(false, false, true);
    }

    private void verifyWorkflow(boolean keepOpen, boolean explicitRefresh) throws Exception {
        verifyWorkflow(keepOpen, explicitRefresh, false);
    }

    private void verifyWorkflow(boolean keepOpen, boolean explicitRefresh, boolean customCsv) throws Exception {
        Path source = directory.resolve("Edmund's Church2.5");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"a16709513_wkt\",\"name\":\"Edmund Church\"}");
        var json = new ObjectMapper();
        var strings = json.createObjectNode();
        for (int index = 0; index < 187; index++) {
            strings.put("entry" + index, "Original " + index);
        }
        json.writeValue(source.resolve("data/strings/strings.json").toFile(), strings);
        // Use precisely the deterministic destination chosen by SsmtApplication.createProject.
        Path folder = SsmtApplication.artifactDirectory(source, null, "Edmund Church");
        Files.createDirectories(folder);
        Path file = folder.resolve(SsmtApplication.artifactBaseName("Edmund Church") + " project.ssmt.json");
        TranslationEditorController editor = new TranslationEditorController();
        ProjectWorkspaceController workspace = new ProjectWorkspaceController(editor);
        workspace.create(source, file, "church.translation", "Church English");
        assertThat(editor.rows()).hasSize(187);
        for (var row : editor.rows()) {
            editor.updateTranslation(row.id(), "Translated " + row.id().key());
        }
        workspace.save();
        var saved = new LocalizationProjectService().read(file);
        assertThat(saved.entries()).allMatch(e -> !e.translatedText().isBlank());
        if (keepOpen) {
            editor.updateTranslation(editor.rows().getFirst().id(), "Unsaved editor revision");
        }
        Path schema = directory.resolve("csv-schema.json");
        Files.createDirectories(source.resolve("data/hulls"));
        if (customCsv) {
            StringBuilder csv = new StringBuilder("id,description\n");
            for (int index = 0; index < 20; index++) {
                csv.append("extra").append(index).append(",New ship ").append(index).append('\n');
            }
            Files.writeString(source.resolve("data/hulls/extra.csv"), csv);
            Files.writeString(schema, """
                    {"schemaVersion":1,"files":[{"path":"data/hulls/extra.csv",
                    "identityColumns":["id"],"textColumns":["description"]}]}
                    """);
        } else {
            for (int index = 0; index < 20; index++) {
                Files.writeString(source.resolve("data/hulls/extra" + index + ".ship"),
                        "{\"hullId\":\"extra" + index + "\",\"hullName\":\"New ship " + index + "\"}");
            }
        }
        if (!keepOpen) {
            editor = new TranslationEditorController();
            workspace = new ProjectWorkspaceController(editor);
        }
        if (explicitRefresh) {
            workspace.open(source, file);
            workspace.applyRefresh(workspace.previewRefresh());
        } else if (customCsv) {
            workspace.createWithCsvSchema(source, file, "unused.new.id", "Unused New Name", schema);
        } else {
            // Same public call as the Start button; existing file must route to refresh.
            workspace.create(source, file, "unused.new.id", "Unused New Name");
        }
        var refreshed = new LocalizationProjectService().read(file);
        assertThat(refreshed.entries()).hasSize(207);
        assertThat(refreshed.entries().stream().filter(e -> !e.translatedText().isBlank())).hasSize(187);
        assertThat(refreshed.entries().stream().filter(e -> e.translatedText().isBlank())).hasSize(20);
        assertThat(refreshed.patchId()).isEqualTo("church.translation");
        assertThat(refreshed.patchName()).isEqualTo("Church English");
        for (var old : saved.entries()) {
            var current = refreshed.entries().stream().filter(e -> e.key().equals(old.key())
                    && e.sourceFile().equals(old.sourceFile())).findFirst().orElseThrow();
            String expected = keepOpen && old.key().equals(saved.entries().getFirst().key())
                    ? "Unsaved editor revision" : old.translatedText();
            assertThat(current.translatedText()).isEqualTo(expected);
            assertThat(current.provenance()).isEqualTo(old.provenance());
        }
        assertThat(editor.rows()).hasSize(207);
        Path exported = folder.resolve("Edmund Church words.json");
        workspace.exportAiPackage(exported, "Edmund Church", "zh", "en", 250);
        var export = json.readTree(exported.toFile());
        assertThat(export.path("entries").size()).isEqualTo(207);
        int translated = 0;
        for (var item : export.path("entries")) {
            String id = item.path("id").asText();
            var current = refreshed.entries().stream().filter(e -> id.equals(
                    e.sourceFile().toString().replace('\\', '/') + "#" + e.key())).findFirst().orElseThrow();
            assertThat(item.path("translation").asText()).isEqualTo(current.translatedText());
            assertThat(item.path("existingTranslation").asText()).isEqualTo(current.translatedText());
            if (!item.path("translation").asText().isBlank()) {
                translated++;
            }
        }
        assertThat(translated).isEqualTo(187);
    }
}
