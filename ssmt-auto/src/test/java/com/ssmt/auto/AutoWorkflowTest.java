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
        AutoRunResult result = new AutoWorkflow(
                        temporaryDirectory.resolve("internal/shared/catalog.db"), workspaceRoot)
                .runDropped(archive);

        assertThat(result.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(result.workspace().getParent()).isEqualTo(workspaceRoot);
        assertThat(userFiles.resolve("Project Go - Archive Mod")).doesNotExist();
        assertThat(userFiles.resolve("Archive Mod - AI translation request.json"))
                .isRegularFile();
        assertThat(result.workspace().resolve("Archive Mod - AI translation request.json"))
                .doesNotExist();
        assertThat(sha256(archive)).isEqualTo(archiveHash);
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
