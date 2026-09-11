package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        Path missing = userFiles.resolve("Example Mod - AI translation request.json");
        Path response = userFiles.resolve("Example Mod - AI translation library.json");
        assertThat(missing).isRegularFile();
        assertThat(workspace.resolve("Example Mod - AI translation request.json")).doesNotExist();
        ObjectNode translated = (ObjectNode) JSON.readTree(missing.toFile());
        translated.put("translatedModName", "Example");
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "The fleet is ready for you");
        JSON.writerWithDefaultPrettyPrinter().writeValue(response.toFile(), translated);

        AutoRunResult built = workflow.run(source);
        AutoRunResult unchanged = workflow.run(source);

        assertThat(built.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(unchanged.status()).isEqualTo(AutoRunResult.Status.PATCH_UNCHANGED);
        Path translatedClone = userFiles.resolve("example.mod.english");
        assertThat(translatedClone.resolve("mod_info.json"))
                .isRegularFile();
        assertThat(userFiles.resolve("example.mod.english-source-backup")).doesNotExist();
        assertThat(translatedClone.resolve("Project Go Changes.csv")).doesNotExist();
        assertThat(sharedCatalog).isRegularFile();
        assertThat(workspace.resolve("project-go-catalog.db")).doesNotExist();
        assertThat(workspace.resolve("Example - Example Mod.ssmt.json")).isRegularFile();
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
        assertThat(userFiles.resolve("Archive Mod - AI translation request.json"))
                .isRegularFile();
        assertThat(result.workspace().resolve("Archive Mod - AI translation request.json"))
                .doesNotExist();
        assertThat(sha256(archive)).isEqualTo(archiveHash);

        ObjectNode translated = (ObjectNode) JSON.readTree(
                userFiles.resolve("Archive Mod - AI translation request.json").toFile());
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated archive line");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("returned-with-a-different-name.json").toFile(), translated);

        AutoRunResult completed = workflow.runDropped(archive);

        assertThat(completed.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(userFiles.resolve("archive.mod.english")).isDirectory();
        assertThat(userFiles.resolve("archive.mod.english-source-backup")).doesNotExist();
        assertThat(userFiles.resolve("Project Go - Archive Mod")).doesNotExist();
        assertThat(sha256(archive)).isEqualTo(archiveHash);
    }

    @Test
    void acceptsMatchingResponseUnderAnyJsonFilenameAndIgnoresRequest() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        Path sharedCatalog = temporaryDirectory.resolve("internal/shared/catalog.db");
        AutoWorkflow workflow = new AutoWorkflow(
                sharedCatalog, temporaryDirectory.resolve("internal/projects"));

        AutoRunResult waiting = workflow.run(source);
        Path request = userFiles.resolve("Example Mod - AI translation request.json");
        ObjectNode translated = (ObjectNode) JSON.readTree(request.toFile());
        translated.put("translatedModName", "Example");
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated line");
        Path renamedResponse = userFiles.resolve("answer-from-ai.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(renamedResponse.toFile(), translated);

        AutoRunResult built = workflow.run(source);

        assertThat(waiting.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(built.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(userFiles.resolve("example.mod.english")).isDirectory();
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
        Path request = userFiles.resolve("Example Mod - AI translation request.json");
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
        Path request = userFiles.resolve("Example Mod - AI translation request.json");
        ObjectNode translated = (ObjectNode) JSON.readTree(request.toFile());
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "A translated line");
        translated.put("entryIdsSha256", "not-the-exported-entry-set");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("plausible-but-corrupt.json").toFile(), translated);

        AutoRunResult stillWaiting = workflow.run(source);

        assertThat(stillWaiting.status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(userFiles.resolve("example.mod.english")).doesNotExist();
    }

    @Test
    void staleDocumentedResponseDoesNotMaskNewRenamedResponse() throws Exception {
        Path userFiles = temporaryDirectory.resolve("user-files");
        Path source = createMod("user-files/Example", "example.mod", "Example Mod");
        AutoWorkflow workflow = new AutoWorkflow(
                temporaryDirectory.resolve("internal/shared/catalog.db"),
                temporaryDirectory.resolve("internal/projects"));
        workflow.run(source);
        Path request = userFiles.resolve("Example Mod - AI translation request.json");
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
                userFiles.resolve("Example Mod - AI translation request.json").toFile());
        response.put("targetLanguage", "fr");
        ((ObjectNode) response.withArray("entries").get(0)).put("translation", "Une ligne");
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                userFiles.resolve("french-answer.json").toFile(), response);

        assertThat(workflow.run(source).status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(userFiles.resolve("example.mod.english")).doesNotExist();
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
