package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.CancellationToken;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectEntry;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.ReconciliationReport;
import com.ssmt.tm.SqliteTranslationMemory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectWorkspaceControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesCentralizedWorkspaceAndRecoveryLocations() throws Exception {
        FakeWorkflow workflow = new FakeWorkflow();
        ProjectWorkspaceController controller = new ProjectWorkspaceController(
                workflow, new TranslationEditorController());
        Path source = temporaryDirectory.resolve("source");
        Path project = temporaryDirectory.resolve("work/example.ssmt.json");
        Path output = temporaryDirectory.resolve("output/example-translation");
        Path memory = temporaryDirectory.resolve("catalog.db");

        controller.create(source, project, "example.translation", "Example Translation");
        controller.build(output);
        ProjectWorkspaceInfo info = controller.workspaceInfo(memory);

        assertThat(info.sourceRoot()).contains(source.toAbsolutePath().normalize());
        assertThat(info.projectFile()).contains(project.toAbsolutePath().normalize());
        assertThat(info.outputRoot()).contains(output.toAbsolutePath().normalize());
        assertThat(info.sourceBackupRoot()).contains(output.toAbsolutePath().resolveSibling(
                output.getFileName() + "-source-backup"));
        assertThat(info.translationMemory()).contains(memory);
        assertThat(info.recoveryRoot()).contains(
                Objects.requireNonNull(project.toAbsolutePath().getParent())
                        .resolve(".ssmt-recovery"));
    }

    @Test
    void opensExistingHealthyTranslationMemoryAndRejectsInvalidFile()
            throws Exception {
        Path valid = temporaryDirectory.resolve("existing.db");
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(valid)) {
            assertThat(memory.verifyIntegrity()).isTrue();
        }
        Path invalid = temporaryDirectory.resolve("invalid.db");
        Files.writeString(invalid, "not sqlite");
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());

        controller.verifyTranslationMemory(valid);

        assertThatThrownBy(() -> controller.verifyTranslationMemory(invalid))
                .isInstanceOf(com.ssmt.project.ProjectException.class)
                .hasMessageContaining("Could not open");
    }

    @Test
    void openOrCreateTranslationMemoryCreatesMissingDefaultCatalogThenPreservesDataAcrossReopen()
            throws Exception {
        Path database = temporaryDirectory.resolve("first-launch-catalog.db");
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());
        assertThat(Files.exists(database)).isFalse();

        controller.openOrCreateTranslationMemory(database);

        assertThat(Files.isRegularFile(database)).isTrue();
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.create(new com.ssmt.tm.TranslationDraft("Hello", "en", "fr", "Bonjour", ""));
        }

        controller.openOrCreateTranslationMemory(database);

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            assertThat(memory.findAll()).singleElement()
                    .extracting(com.ssmt.tm.TranslationEntry::translatedText)
                    .isEqualTo("Bonjour");
        }
    }

    @Test
    void openOrCreateTranslationMemoryCreatesMissingParentDirectories() throws Exception {
        Path database = temporaryDirectory.resolve("nested/does-not-exist-yet/catalog.db");
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());
        assertThat(Files.exists(database.getParent())).isFalse();

        controller.openOrCreateTranslationMemory(database);

        assertThat(Files.isRegularFile(database)).isTrue();
    }

    @Test
    void catalogComparisonReportsTheUnderlyingActionableReason() throws Exception {
        Path database = temporaryDirectory.resolve("catalog.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            assertThat(memory.verifyIntegrity()).isTrue();
        }
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());

        assertThatThrownBy(() ->
                        controller.compareTranslationMemories(database, database))
                .isInstanceOf(com.ssmt.project.ProjectException.class)
                .hasMessageContaining("same file");
    }

    @Test
    void createsEditsSavesAndBuildsCurrentProject() throws Exception {
        FakeWorkflow workflow = new FakeWorkflow();
        TranslationEditorController editor = new TranslationEditorController();
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(workflow, editor);
        Path source = Path.of("source").toAbsolutePath();
        Path projectFile = Path.of("translation.ssmt.json").toAbsolutePath();

        controller.create(source, projectFile, "example.fr", "Example French");
        editor.updateTranslation(editor.rows().getFirst().id(), "Salut");
        controller.save();
        ProjectBuildResult result =
                controller.build(Path.of("output").toAbsolutePath());
        ProjectRefreshResult refresh = controller.previewRefresh();
        ProjectRefreshResult memoryRefresh =
                controller.previewRefreshWithTranslationMemory(
                        Path.of("memory.db"), "en", "fr", 0.8);
        controller.applyRefresh(refresh);

        assertThat(workflow.written.entries().getFirst().translatedText()).isEqualTo("Salut");
        assertThat(workflow.built.entries().getFirst().translatedText()).isEqualTo("Salut");
        assertThat(result.changed()).isTrue();
        assertThat(memoryRefresh.project()).isNotNull();
        assertThat(controller.projectFile()).contains(projectFile);
        assertThat(controller.sourceRoot()).contains(source);
    }

    @Test
    void realControllerWorkflowBuildsDeterministicallyWithoutTouchingSource()
            throws Exception {
        Path source = temporaryDirectory.resolve("source-mod");
        Files.createDirectories(source.resolve("data/strings"));
        Path metadata = source.resolve("mod_info.json");
        Path strings = source.resolve("data/strings/strings.json");
        Files.writeString(metadata, "{\"id\":\"example\",\"name\":\"Example\"}");
        Files.writeString(strings, "{\"welcome\":\"Hello\"}");
        byte[] metadataBytes = Files.readAllBytes(metadata);
        byte[] stringBytes = Files.readAllBytes(strings);
        FileTime metadataTime = Files.getLastModifiedTime(metadata);
        FileTime stringTime = Files.getLastModifiedTime(strings);
        Path project = temporaryDirectory.resolve("example.ssmt.json");
        Path output = temporaryDirectory.resolve("example-en");
        TranslationEditorController editor = new TranslationEditorController();
        ProjectWorkspaceController controller = new ProjectWorkspaceController(editor);

        controller.create(source, project, "example.en", "Example English");
        editor.updateTranslation(editor.rows().getFirst().id(), "Welcome");
        controller.save();
        assertThat(controller.previewRefresh().project().entries().getFirst().translatedText())
                .isEqualTo("Welcome");
        assertThat(controller.build(output).changed()).isTrue();
        byte[] firstPatch = Files.readAllBytes(output.resolve("data/strings/strings.json"));
        assertThat(controller.build(output).changed()).isFalse();
        assertThat(Files.readAllBytes(output.resolve("data/strings/strings.json")))
                .isEqualTo(firstPatch);

        assertThat(Files.readAllBytes(metadata)).isEqualTo(metadataBytes);
        assertThat(Files.readAllBytes(strings)).isEqualTo(stringBytes);
        assertThat(Files.getLastModifiedTime(metadata)).isEqualTo(metadataTime);
        assertThat(Files.getLastModifiedTime(strings)).isEqualTo(stringTime);
    }

    @Test
    void realControllerCreatesProjectWithExplicitCustomCsvSchema() throws Exception {
        Path source = temporaryDirectory.resolve("csv-schema-source");
        Files.createDirectories(source.resolve("data/hulls"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"csv.schema.example\",\"name\":\"Example\"}");
        Files.writeString(
                source.resolve("data/hulls/custom_hull_extra.csv"),
                "id,flavorText\nhull_one,Visible flavor text\n");
        Path schema = temporaryDirectory.resolve("csv-schema.json");
        Files.writeString(schema, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/hulls/custom_hull_extra.csv",
                    "identityColumns": ["id"],
                    "textColumns": ["flavorText"]
                  }]
                }
                """);
        Path project = temporaryDirectory.resolve("csv-schema.ssmt.json");
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());

        controller.createWithCsvSchema(source, project, "csv.schema.fr", "Custom French", schema);

        assertThat(controller.sourceRoot()).contains(source);
        assertThat(Files.exists(project)).isTrue();
    }

    private static final class FakeWorkflow
            implements ProjectWorkspaceController.ProjectWorkflow {
        private LocalizationProject written;
        private LocalizationProject built;

        @Override
        public LocalizationProject create(
                Path sourceRoot,
                String patchId,
                String patchName,
                Optional<Path> jsonSchemaCatalog,
                Optional<Path> csvSchemaCatalog,
                CancellationToken cancellation) {
            cancellation.throwIfCancellationRequested();
            return project();
        }

        @Override
        public LocalizationProject read(Path source) {
            return project();
        }

        @Override
        public void write(Path destination, LocalizationProject project) {
            written = project;
        }

        @Override
        public ProjectBuildResult build(
                Path sourceRoot,
                Path outputRoot,
                LocalizationProject project,
                CancellationToken cancellation) {
            cancellation.throwIfCancellationRequested();
            built = project;
            return new ProjectBuildResult(true, 1);
        }

        @Override
        public ProjectRefreshResult refresh(
                Path sourceRoot,
                LocalizationProject project,
                CancellationToken cancellation) {
            cancellation.throwIfCancellationRequested();
            return new ProjectRefreshResult(project, new ReconciliationReport(List.of()));
        }

        @Override
        public ProjectRefreshResult refreshWithTranslationMemory(
                Path sourceRoot,
                LocalizationProject project,
                Path translationMemory,
                String sourceLanguage,
                String targetLanguage,
                double minimumScore,
                CancellationToken cancellation) {
            return refresh(sourceRoot, project, cancellation);
        }

        private static LocalizationProject project() {
            return new LocalizationProject(
                    1,
                    "example",
                    "example.fr",
                    "Example French",
                    List.of(new ProjectEntry(
                            Path.of("data/strings/strings.json"),
                            "json:/welcome",
                            "Hello",
                            "Bonjour")));
        }
    }
}
