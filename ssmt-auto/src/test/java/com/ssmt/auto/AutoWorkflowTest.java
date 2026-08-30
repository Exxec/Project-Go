package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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
                "shared/ssmt-english-catalog.db");
        AutoWorkflow workflow = new AutoWorkflow(sharedCatalog);

        AutoRunResult waiting = workflow.run(source);

        assertThat(waiting.status())
                .isEqualTo(AutoRunResult.Status.WAITING_FOR_TRANSLATION);
        Path workspace = temporaryDirectory.resolve("SSMT Auto - Example Mod");
        Path missing = workspace.resolve("Example Mod - words-to-translate.json");
        Path response = workspace.resolve("Example Mod - words-translated.json");
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
        assertThat(workspace.resolve("ssmt-english-catalog.db")).doesNotExist();
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
                .isEqualTo(AutoRunResult.Status.WAITING_FOR_TRANSLATION);
        assertThat(JSON.readTree(missing.toFile()).withArray("entries").size())
                .isEqualTo(1);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
