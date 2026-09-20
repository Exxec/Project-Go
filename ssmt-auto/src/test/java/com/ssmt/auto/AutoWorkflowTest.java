package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.WorkflowPersistenceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoWorkflowTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void waitsForNamedResponseThenBuildsAndRefreshesUpdatedMod() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = userFiles.resolve("Example Mod");
        Files.createDirectories(source.resolve("data/strings"));
        Path metadata = source.resolve("mod_info.json");
        Path strings = source.resolve("data/strings/strings.json");
        Files.writeString(
                metadata,
                "{\"id\":\"example.mod\",\"name\":\"Example Mod\",\"version\":\"1\"}",
                StandardCharsets.UTF_8);
        Files.writeString(
                strings,
                "{\"welcome\":\"The fleet is ready for you\"}",
                StandardCharsets.UTF_8);
        String sourceHash = sha256(strings);
        Path sharedCatalog = temporaryDirectory.resolve(
                "internal/shared/project-go-catalog.db");
        Path workspaceRoot = temporaryDirectory.resolve("internal/app-data/projects");
        AutoWorkflow workflow = new AutoWorkflow(sharedCatalog, workspaceRoot);

        AutoRunResult waiting = workflow.run(source);

        assertThat(waiting.status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        Path workspace = waiting.workspace();
        assertThat(workspace).isDirectory();
        assertThat(workspace.getParent()).isEqualTo(workspaceRoot);
        assertThat(userFiles.resolve("Project Go - Example Mod")).doesNotExist();
        Path missing = userFiles.resolve("Example Mod - Translate to English.json");
        Path response = userFiles.resolve("Example Mod - AI translation library.json");
        assertThat(missing).isRegularFile();
        assertThat(workspace.resolve("Example Mod - Translate to English.json")).doesNotExist();
        ObjectNode translated = (ObjectNode) JSON.readTree(missing.toFile());
        translated.put("translatedModName", "Example");
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "The fleet is ready for you");
        JSON.writerWithDefaultPrettyPrinter().writeValue(response.toFile(), translated);

        AutoRunResult built = workflow.run(source);
        AutoRunResult unchanged = workflow.run(source);

        assertThat(built.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(unchanged.status()).isEqualTo(AutoRunResult.Status.PATCH_UNCHANGED);
        Path translatedClone = userFiles.resolve("Example Mod - English");
        assertThat(translatedClone.resolve("mod_info.json"))
                .isRegularFile();
        assertThat(userFiles.resolve("Example Mod - English-source-backup")).doesNotExist();
        assertThat(translatedClone.resolve("Project Go Changes.csv")).doesNotExist();
        assertThat(sharedCatalog).isRegularFile();
        assertThat(workspace.resolve("project-go-catalog.db")).doesNotExist();
        assertThat(workspace.resolve("Example - Example Mod.ssmt.json")).isRegularFile();
        JsonNode attestations = JSON.readTree(workspace.resolve("source-attestations.json").toFile())
                .path("attestations");
        assertThat(attestations.toString()).contains(
                "CREATE_EXTRACTION", "REFRESH_EXTRACTION", "IMPORT_RESPONSE", "BUILD_CLONE");
        assertThat(sha256(strings)).isEqualTo(sourceHash);

        Files.writeString(
                metadata,
                "{\"id\":\"example.mod\",\"name\":\"Example Mod\",\"version\":\"2\"}",
                StandardCharsets.UTF_8);
        Files.writeString(
                strings,
                """
                {
                  "welcome": "The fleet is ready for you",
                  "new": "A new untranslated sentence"
                }
                """,
                StandardCharsets.UTF_8);

        AutoRunResult updated = workflow.run(source);

        assertThat(updated.status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_INCOMPLETE);
        assertThat(JSON.readTree(missing.toFile()).withArray("entries").size())
                .isEqualTo(1);
    }

    @Test
    void refreshesChangedSourceEvenWhenDeclaredVersionIsUnchanged() throws Exception {
        Path source = temporaryDirectory.resolve("Same version");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"same.version\",\"name\":\"Same version\",\"version\":\"1\"}");
        Path strings = source.resolve("data/strings/strings.json");
        Files.writeString(strings, "{\"welcome\":\"Original message\"}");
        Path catalog = temporaryDirectory.resolve("internal/catalog.db");
        AutoWorkflow workflow = new AutoWorkflow(catalog,
                temporaryDirectory.resolve("internal/projects"));
        workflow.run(source);
        Path request = temporaryDirectory.resolve("Same version - Translate to English.json");
        ObjectNode response = (ObjectNode) JSON.readTree(request.toFile());
        ((ObjectNode) response.withArray("entries").get(0))
                .put("translation", "Translated original message");
        Path responseFile = temporaryDirectory.resolve("returned.json");
        JSON.writeValue(responseFile.toFile(), response);
        assertThat(workflow.run(source).status())
                .isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        Files.delete(responseFile);
        Files.writeString(strings, "{\"welcome\":\"Changed message\",\"added\":\"New message\"}");
        String changedHash = sha256(strings);

        assertThat(workflow.run(source).status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_INCOMPLETE);
        var entries = JSON.readTree(request.toFile()).withArray("entries");
        assertThat(entries.size()).isEqualTo(2);
        assertThat(entries.toString()).contains("Changed message", "New message")
                .doesNotContain("Original message", "Translated original message");
        assertThat(sha256(strings)).isEqualTo(changedHash);
    }

    @Test
    void acceptsZipArchiveWithSingleNestedModFolder() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Files.createDirectories(userFiles);
        Path archive = userFiles.resolve("Archive Mod.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeZipEntry(output, "Archive Mod/mod_info.json",
                    "{\"id\":\"archive.mod\",\"name\":\"Archive Mod\",\"version\":\"1\"}");
            writeZipEntry(output, "Archive Mod/data/strings/strings.json",
                    "{\"welcome\":\"A line from an archive\"}");
        }
        String archiveHash = sha256(archive);

        Path workspaceRoot = temporaryDirectory.resolve("internal/app-data/projects");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"), workspaceRoot);
        AutoRunResult result = workflow.runDropped(archive);

        assertThat(result.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(result.workspace().getParent()).isEqualTo(workspaceRoot);
        assertThat(userFiles.resolve("Project Go - Archive Mod")).doesNotExist();
        assertThat(userFiles.resolve("Archive Mod - Translate to English.json"))
                .isRegularFile();
        assertThat(result.workspace().resolve("Archive Mod - Translate to English.json"))
                .doesNotExist();
        assertThat(sha256(archive)).isEqualTo(archiveHash);

        ObjectNode translated = (ObjectNode) JSON.readTree(
                userFiles.resolve("Archive Mod - Translate to English.json").toFile());
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated archive line");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("returned-with-a-different-name.json").toFile(), translated);

        AutoRunResult completed = workflow.runDropped(archive);

        assertThat(completed.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(userFiles.resolve("Archive Mod - English")).isDirectory();
        assertThat(userFiles.resolve("Archive Mod - English-source-backup")).doesNotExist();
        assertThat(userFiles.resolve("Project Go - Archive Mod")).doesNotExist();
        assertThat(sha256(archive)).isEqualTo(archiveHash);
    }

    @Test
    void droppedFolderAndMetadataUseTheSameWorkspaceAndVisibleFiles() throws Exception {
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        Path workspaceRoot = temporaryDirectory.resolve("internal/projects");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"), workspaceRoot);

        AutoRunResult folder = workflow.runDropped(source);
        AutoRunResult metadata = workflow.runDropped(source.resolve("mod_info.json"));

        assertThat(folder.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(metadata.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(metadata.workspace()).isEqualTo(folder.workspace());
        assertThat(folder.workspace().getParent()).isEqualTo(workspaceRoot);
        assertThat(source.getParent().resolve("Example Mod - Translate to English.json"))
                .isRegularFile();
        assertThat(workspaceRoot.resolve("input-cache")).doesNotExist();
    }

    @Test
    void acceptsMatchingResponseUnderAnyJsonFilenameAndIgnoresRequest() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        Path sharedCatalog = temporaryDirectory.resolve("internal/shared/catalog.db");
        AutoWorkflow workflow = new AutoWorkflow(
                sharedCatalog, temporaryDirectory.resolve("internal/projects"));

        AutoRunResult waiting = workflow.run(source);
        Path request = userFiles.resolve("Example Mod - Translate to English.json");
        ObjectNode translated = (ObjectNode) JSON.readTree(request.toFile());
        translated.put("translatedModName", "Example");
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated line");
        Path renamedResponse = userFiles.resolve("answer-from-ai.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(renamedResponse.toFile(), translated);

        AutoRunResult built = workflow.run(source);

        assertThat(waiting.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(built.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(userFiles.resolve("Example Mod - English")).isDirectory();
        assertThat(request).isRegularFile();
    }

    @Test
    void ignoresUnrelatedJsonAndRejectsAmbiguousMatchingResponses() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        workflow.run(source);
        Path request = userFiles.resolve("Example Mod - Translate to English.json");
        ObjectNode translated = (ObjectNode) JSON.readTree(request.toFile());
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated line");
        Files.writeString(userFiles.resolve("unrelated.json"),
                "{\"sourceModId\":\"another.mod\"}", StandardCharsets.UTF_8);
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("first-answer.json").toFile(), translated);
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A different translated line");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("second-answer.json").toFile(), translated);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.run(source))
                .isInstanceOf(com.ssmt.project.ProjectException.class)
                .hasMessageContaining("More than one new AI response matches this mod");
    }

    @Test
    void ignoresResponseWhoseEmbeddedEntryIntegrityDoesNotMatch() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        workflow.run(source);
        Path request = userFiles.resolve("Example Mod - Translate to English.json");
        ObjectNode translated = (ObjectNode) JSON.readTree(request.toFile());
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated line");
        translated.put("entryIdsSha256", "not-the-exported-entry-set");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("plausible-but-corrupt.json").toFile(), translated);

        AutoRunResult stillWaiting = workflow.run(source);

        assertThat(stillWaiting.status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(userFiles.resolve("Example Mod - English")).doesNotExist();
    }

    @Test
    void staleDocumentedResponseDoesNotMaskNewRenamedResponse() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        workflow.run(source);
        Path request = userFiles.resolve("Example Mod - Translate to English.json");
        ObjectNode first = (ObjectNode) JSON.readTree(request.toFile());
        ((ObjectNode) first.withArray("entries").get(0)).put("translation", "First response");
        Path documented = userFiles.resolve("Example Mod - AI translation library.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(documented.toFile(), first);
        workflow.run(source);

        Files.writeString(source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"An untranslated line\",\"second\":\"Another line\"}");
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"example.mod\",\"name\":\"Example Mod\",\"version\":\"2\"}");
        workflow.run(source);
        ObjectNode second = (ObjectNode) JSON.readTree(request.toFile());
        ((ObjectNode) second.withArray("entries").get(0)).put("translation", "Second response");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("new-answer.json").toFile(), second);

        assertThat(workflow.run(source).status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
    }

    @Test
    void ignoresMatchingResponseForAnotherTargetLanguage() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        workflow.run(source);
        ObjectNode response = (ObjectNode) JSON.readTree(
                userFiles.resolve("Example Mod - Translate to English.json").toFile());
        response.put("targetLanguage", "fr");
        ((ObjectNode) response.withArray("entries").get(0)).put("translation", "Une ligne");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("french-answer.json").toFile(), response);

        assertThat(workflow.run(source).status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(userFiles.resolve("Example Mod - English")).doesNotExist();
    }

    @Test
    void givesSameNamedModsCollisionSafeStableWorkspaces() throws Exception {
        Path first = createMod("first/Shared", "same.id", "Shared Mod");
        Path second = createMod("second/Shared", "same.id", "Shared Mod");
        Path workspaceRoot = temporaryDirectory.resolve("app-data/projects");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("shared/catalog.db"), workspaceRoot);

        AutoRunResult firstRun = workflow.run(first);
        AutoRunResult resumedFirstRun = workflow.run(first);
        AutoRunResult secondRun = workflow.run(second);

        assertThat(resumedFirstRun.workspace()).isEqualTo(firstRun.workspace());
        assertThat(secondRun.workspace()).isNotEqualTo(firstRun.workspace());
        assertThat(firstRun.workspace().getParent()).isEqualTo(workspaceRoot);
        assertThat(secondRun.workspace().getParent()).isEqualTo(workspaceRoot);
    }

    @Test
    void persistsSharedWorkflowBoundaryAndMigratesVersionOneState() throws Exception {
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));

        AutoRunResult first = workflow.run(source);
        Path stateFile = first.workspace().resolve("project-go-state.json");
        ObjectNode state = (ObjectNode) JSON.readTree(stateFile.toFile());

        assertThat(state.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(state.path("workflow").path("phase").asText())
                .isEqualTo("RESPONSE_PENDING");
        assertThat(state.path("workflow").path("sourceModId").asText())
                .isEqualTo("example.mod");
        assertThat(state.path("workflow").path("entrySetSha256").asText())
                .hasSize(64);

        state.put("schemaVersion", 1);
        state.remove("workflow");
        JSON.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);

        AutoRunResult migrated = workflow.run(source);
        JsonNode migratedState = JSON.readTree(stateFile.toFile());
        assertThat(migrated.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(migratedState.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(migratedState.path("workflow").path("phase").asText())
                .isEqualTo("RESPONSE_PENDING");
    }

    @Test
    void rejectsPersistedWorkflowBindingDriftWithoutChangingSourceOrProject()
            throws Exception {
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        AutoRunResult first = workflow.run(source);
        Path stateFile = first.workspace().resolve("project-go-state.json");
        ObjectNode state = (ObjectNode) JSON.readTree(stateFile.toFile());
        Path projectFile = first.workspace().resolve(state.path("projectFile").asText());
        String sourceHash = sha256(source.resolve("data/strings/strings.json"));
        String projectHash = sha256(projectFile);
        ((ObjectNode) state.path("workflow")).put("entrySetSha256", "0".repeat(64));
        JSON.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.run(source))
                .isInstanceOf(com.ssmt.project.ProjectException.class)
                .hasMessageContaining("previous workflow step");
        assertThat(sha256(source.resolve("data/strings/strings.json"))).isEqualTo(sourceHash);
        assertThat(sha256(projectFile)).isEqualTo(projectHash);
    }

    @Test
    void rejectedResponseAfterSourceRefreshLeavesCommittedProjectUnchanged()
            throws Exception {
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        AutoRunResult first = workflow.run(source);
        JsonNode state = JSON.readTree(
                first.workspace().resolve("project-go-state.json").toFile());
        Path projectFile = first.workspace().resolve(state.path("projectFile").asText());
        String committedHash = sha256(projectFile);
        Files.writeString(source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Changed source text\",\"new\":\"New text\"}");
        Files.writeString(temporaryDirectory.resolve(
                        "user-files/Example Mod - AI translation library.json"),
                "{\"sourceModId\":\"wrong.mod\"}");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.run(source))
                .isInstanceOf(com.ssmt.project.ProjectException.class)
                .hasMessageContaining("does not match the current English translation request");
        assertThat(sha256(projectFile)).isEqualTo(committedHash);
    }

    @Test
    void failedProjectStatePublicationRollsBackBothDocuments() throws Exception {
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        Path catalog = temporaryDirectory.resolve("internal/shared/catalog.db");
        Path workspaces = temporaryDirectory.resolve("internal/projects");
        AutoRunResult first = new AutoWorkflow(catalog, workspaces).run(source);
        Path stateFile = first.workspace().resolve("project-go-state.json");
        JsonNode state = JSON.readTree(stateFile.toFile());
        Path projectFile = first.workspace().resolve(state.path("projectFile").asText());
        String projectHash = sha256(projectFile);
        String stateHash = sha256(stateFile);
        Files.writeString(source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Changed after the committed state\"}");
        java.util.concurrent.atomic.AtomicInteger publications =
                new java.util.concurrent.atomic.AtomicInteger();
        var persistence = new WorkflowPersistenceService((staging, target) -> {
            if (publications.incrementAndGet() == 2) {
                throw new IOException("injected state publication failure");
            }
            Files.move(staging, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        });
        AutoWorkflow failing = new AutoWorkflow(catalog, workspaces, persistence);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> failing.run(source))
                .isInstanceOf(com.ssmt.project.ProjectException.class)
                .hasMessageContaining("previous state was retained");
        assertThat(sha256(projectFile)).isEqualTo(projectHash);
        assertThat(sha256(stateFile)).isEqualTo(stateHash);
        assertThat(first.workspace().resolve(".workflow-transaction")).doesNotExist();
    }

    private Path createMod(String relativePath, String id, String name) throws Exception {
        Path source = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"" + id + "\",\"name\":\"" + name
                        + "\",\"version\":\"1\"}",
                StandardCharsets.UTF_8);
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"An untranslated line\"}",
                StandardCharsets.UTF_8);
        return source;
    }

    private static void writeZipEntry(ZipOutputStream output, String name, String content)
            throws java.io.IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
