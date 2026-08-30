package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BackendCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsCurrentReleaseVersion() throws Exception {
        StringWriter output = new StringWriter();
        CommandLine command = new CommandLine(new Main());
        command.setOut(new PrintWriter(output));

        assertThat(command.execute("--version")).isZero();
        assertThat(output.toString())
                .isEqualTo(new Main.VersionProvider().getVersion()[0]
                        + System.lineSeparator());
    }

    @Test
    void validateReturnsFailureForBrokenPlaceholder() {
        CommandLine command = new CommandLine(new Main());
        assertThat(command.execute("validate", "Hello %s", "Bonjour")).isEqualTo(1);
        assertThat(command.execute("validate", "Hello %s", "Bonjour %s")).isZero();
    }

    @Test
    void translationMemoryExportsAndImportsJson() throws Exception {
        Path source = temporaryDirectory.resolve("source.db");
        Path target = temporaryDirectory.resolve("target.db");
        Path interchange = temporaryDirectory.resolve("translations.json");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(source)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", ""));
        }
        CommandLine command = new CommandLine(new Main());

        assertThat(command.execute(
                        "tm", "export", source.toString(), "json", interchange.toString()))
                .isZero();
        assertThat(command.execute(
                        "tm", "import", target.toString(), "json", interchange.toString()))
                .isZero();
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(target)) {
            assertThat(memory.findAll()).hasSize(1);
        }
    }

    @Test
    void translationMemoryIntegrityAndBackupCommandsSucceed() throws Exception {
        Path database = temporaryDirectory.resolve("memory.db");
        Path backup = temporaryDirectory.resolve("memory-backup.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", ""));
        }
        CommandLine command = new CommandLine(new Main());

        assertThat(command.execute("tm", "integrity", database.toString())).isZero();
        assertThat(command.execute(
                        "tm", "backup", database.toString(), backup.toString()))
                .isZero();
        assertThat(backup).isRegularFile();
    }

    @Test
    void pluginCatalogCommandAcceptsEmptyDirectory() throws Exception {
        Path plugins = temporaryDirectory.resolve("plugins");
        Files.createDirectories(plugins);

        assertThat(new CommandLine(new Main()).execute("plugins", plugins.toString())).isZero();
    }

    @Test
    void projectCommandsCreateAndBuildAnEditableOverlay() throws Exception {
        Path source = temporaryDirectory.resolve("source-mod");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"example.mod\",\"name\":\"Example\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello\"}");
        Path project = temporaryDirectory.resolve("example.ssmt.json");
        Path output = temporaryDirectory.resolve("example-fr");
        CommandLine command = new CommandLine(new Main());

        assertThat(command.execute(
                        "project",
                        "create",
                        source.toString(),
                        project.toString(),
                        "--patch-id",
                        "example.fr",
                        "--patch-name",
                        "Example French"))
                .isZero();
        Files.writeString(
                project,
                Files.readString(project).replace(
                        "\"translatedText\" : \"\"",
                        "\"translatedText\" : \"Bonjour\""));
        assertThat(command.execute(
                        "project",
                        "build",
                        source.toString(),
                        project.toString(),
                        output.toString()))
                .isZero();
        assertThat(Files.readString(output.resolve("data/strings/strings.json")))
                .contains("Bonjour");
    }

    @Test
    void completeCliWorkflowIsDeterministicZeroTouchAndTransactional() throws Exception {
        Path mods = temporaryDirectory.resolve("mods");
        Path source = mods.resolve("source-mod");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"example.mod\",\"name\":\"Example\",\"version\":\"1.0\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello %s\"}");
        Map<String, SourceState> originalSource = sourceState(source);
        Path workspace = temporaryDirectory.resolve("workspace");
        Path project = workspace.resolve("example.ssmt.json");
        Path output = temporaryDirectory.resolve("published/example-en");
        Files.createDirectories(workspace);
        CommandLine command = new CommandLine(new Main());

        assertThat(command.execute("scan", mods.toString())).isZero();
        assertThat(command.execute("extract", source.toString())).isZero();
        assertThat(command.execute(
                        "project", "create", source.toString(), project.toString(),
                        "--patch-id", "example.en", "--patch-name", "Example English"))
                .isZero();
        String edited = Files.readString(project).replace(
                "\"translatedText\" : \"\"",
                "\"translatedText\" : \"Welcome %s\"");
        Files.writeString(project, edited);
        assertThat(command.execute("validate", "Hello %s", "Welcome %s")).isZero();
        assertThat(command.execute(
                        "project", "build", source.toString(), project.toString(),
                        output.toString()))
                .isZero();
        Map<String, SourceState> firstOutput = sourceState(output);

        assertThat(command.execute(
                        "project", "build", source.toString(), project.toString(),
                        output.toString()))
                .isZero();
        assertThat(sourceState(output)).isEqualTo(firstOutput);
        assertThat(command.execute(
                        "project", "refresh", source.toString(), project.toString()))
                .isZero();
        assertThat(command.execute(
                        "project", "refresh", source.toString(), project.toString(), "--apply"))
                .isZero();

        assertThat(output.normalize()).isNotEqualTo(source.normalize());
        assertThat(sourceState(source)).isEqualTo(originalSource);
        assertThat(Files.readString(project))
                .contains("\"translatedText\" : \"Welcome %s\"")
                .doesNotContain("AI_TRANSLATED", "FUZZY_MATCH");

        Path brokenProject = temporaryDirectory.resolve("broken.ssmt.json");
        Path failedOutput = temporaryDirectory.resolve("failed-output");
        Files.writeString(brokenProject, "{broken");
        assertThat(command.execute(
                        "project", "build", source.toString(), brokenProject.toString(),
                        failedOutput.toString()))
                .isEqualTo(1);
        assertThat(failedOutput).doesNotExist();
        assertThat(sourceState(source)).isEqualTo(originalSource);
    }

    private static Map<String, SourceState> sourceState(Path root) throws Exception {
        Map<String, SourceState> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                byte[] bytes = Files.readAllBytes(path);
                String digest = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytes));
                FileTime modified = Files.getLastModifiedTime(path);
                result.put(root.relativize(path).toString(), new SourceState(digest, modified));
            }
        }
        return result;
    }

    private record SourceState(String sha256, FileTime modified) {
    }
}
