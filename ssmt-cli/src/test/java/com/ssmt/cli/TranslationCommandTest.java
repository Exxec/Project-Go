package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;
import com.ssmt.project.TranslationWorkflow;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private int run(String action, Path source, Path file, Path owned) {
        var args = new java.util.ArrayList<>(java.util.List.of("translation", action, source.toString()));
        if (file != null) { args.add(file.toString()); }
        args.addAll(java.util.List.of("--workspace", owned.toString()));
        return new CommandLine(new Main()).execute(args.toArray(String[]::new));
    }
}
