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
        Path source = temporaryDirectory.resolve("Example Mod");
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
                "shared/project-go-catalog.db");
        AutoWorkflow workflow = new AutoWorkflow(sharedCatalog);

        AutoRunResult waiting = workflow.run(source);

        assertThat(waiting.status())
                .isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        Path workspace = temporaryDirectory.resolve("Project Go - Example Mod");
        Path missing = workspace.resolve("Example Mod - AI translation request.json");
        Path response = workspace.resolve("Example Mod - AI translation library.json");
        assertThat(missing).isRegularFile();
        ObjectNode translated = (ObjectNode) JSON.readTree(missing.toFile());
        translated.put("translatedModName", "Example");
        ((ObjectNode) translated.withArray("entries").get(0))
                .put("translation", "The fleet is ready for you");
        JSON.writerWithDefaultPrettyPrinter().writeValue(response.toFile(), translated);

        AutoRunResult built = workflow.run(source);
        AutoRunResult unchanged = workflow.run(source);

        assertThat(built.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(unchanged.status()).isEqualTo(AutoRunResult.Status.PATCH_UNCHANGED);
        Path translatedClone = workspace.resolve("example.mod.english");
        assertThat(translatedClone.resolve("mod_info.json"))
                .isRegularFile();
        assertThat(workspace.resolve(
                        "example.mod.english-source-backup/mod_info.json"))
                .isRegularFile();
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
        Path archive = temporaryDirectory.resolve("Archive Mod.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeZipEntry(output, "Archive Mod/mod_info.json",
                    "{\"id\":\"archive.mod\",\"name\":\"Archive Mod\",\"version\":\"1\"}");
            writeZipEntry(output, "Archive Mod/data/strings/strings.json",
                    "{\"welcome\":\"A line from an archive\"}");
        }

        AutoRunResult result = new AutoWorkflow(temporaryDirectory.resolve("shared/catalog.db"))
                .runDropped(archive);

        assertThat(result.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(result.workspace().resolve("Archive Mod - AI translation request.json"))
                .isRegularFile();
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
