package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;
import com.ssmt.project.TranslationWorkflow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class TranslationCommandTest {
    @TempDir Path directory;

    @Test void cliUsesTheSameWorkspaceAndFourOperationsAsDesktopFacade() throws Exception {
        Path source = directory.resolve("source");
        Path owned = directory.resolve("owned");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"cli\",\"name\":\"CLI\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        Path response = directory.resolve("response.json");
        assertThat(run("load", source, null, owned)).isZero();
        assertThat(run("export", source, response, owned)).isZero();
        Files.writeString(response, Files.readString(response).replace("\"translation\" : \"\"", "\"translation\" : \"Bonjour\""));
        assertThat(run("import", source, response, owned)).isZero();
        var session = new TranslationWorkflow(owned).loadMod(source);
        assertThat(session.project().entries().getFirst().translatedText()).isEqualTo("Bonjour");
        assertThat(run("build", source, directory.resolve("output"), owned)).isZero();
        assertThat(directory.resolve("output/Project Go Changes.csv")).doesNotExist();
        assertThat(com.ssmt.patcher.PatchBuilder.sourceBackupRoot(
                directory.resolve("output"))).doesNotExist();
        assertThat(Files.readString(source.resolve("data/strings/strings.json"))).contains("Hello").doesNotContain("Bonjour");
    }

    @Test void cliAcceptsZipWithoutChangingItAndModInfoAsTheSameInput() throws Exception {
        Path archive = directory.resolve("wrapped.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("wrapper/mod_info.json"));
            output.write("{\"id\":\"archive-cli\",\"name\":\"Archive CLI\"}".getBytes());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("wrapper/data/strings/strings.json"));
            output.write("{\"hello\":\"Hello\"}".getBytes());
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(archive);
        Path owned = directory.resolve("zip-owned");

        assertThat(run("load", archive, null, owned)).isZero();
        assertThat(Files.readAllBytes(archive)).containsExactly(before);

        Path direct = directory.resolve("direct");
        Files.createDirectories(direct.resolve("data/strings"));
        Files.writeString(direct.resolve("mod_info.json"),
                "{\"id\":\"direct-cli\",\"name\":\"Direct CLI\"}");
        Files.writeString(direct.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        assertThat(run("load", direct.resolve("mod_info.json"), null,
                directory.resolve("direct-owned"))).isZero();
    }

    private int run(String action, Path source, Path file, Path owned) {
        var args = new java.util.ArrayList<>(java.util.List.of("translation", action, source.toString()));
        if (file != null) { args.add(file.toString()); }
        args.addAll(java.util.List.of("--workspace", owned.toString()));
        return new CommandLine(new Main()).execute(args.toArray(String[]::new));
    }

    @Test void cliCanResolveSameIdConflictsWithoutSilentlyMergingWork() throws Exception {
        Path owned = directory.resolve("lineage-owned");
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        for (Path source : java.util.List.of(first, second)) {
            Files.createDirectories(source.resolve("data/strings"));
            Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"same\",\"name\":\"Mod\"}");
        }
        Files.writeString(first.resolve("data/strings/strings.json"), "{\"a\":\"First\"}");
        Files.writeString(second.resolve("data/strings/strings.json"), "{\"a\":\"Second\"}");
        assertThat(run("load", first, null, owned)).isZero();
        var original = new TranslationWorkflow(owned).loadMod(first);
        byte[] before = Files.readAllBytes(original.workspace().resolve("project.ssmt.json"));
        assertThat(run("load", second, null, owned)).isEqualTo(1);
        assertThat(new CommandLine(new Main()).execute("translation", "load", second.toString(),
                "--workspace", owned.toString(), "--start-separately")).isZero();
        assertThat(new TranslationWorkflow(owned).loadMod(second).workspace()).isNotEqualTo(original.workspace());
        assertThat(Files.readAllBytes(original.workspace().resolve("project.ssmt.json"))).isEqualTo(before);
        assertThat(new CommandLine(new Main()).execute("translation", "load", second.toString(),
                "--workspace", owned.toString(), "--use-previous", "--start-separately")).isEqualTo(1);
    }
}
