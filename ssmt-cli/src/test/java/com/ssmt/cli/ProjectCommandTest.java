package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.project.AiTranslationExchangeService;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ProjectCommandTest {
    @TempDir Path temporary;

    @Test
    void importsAiResponseIntoProjectFile() throws Exception {
        LocalizationProjectService service = new LocalizationProjectService();
        Path projectFile = temporary.resolve("project.ssmt.json");
        service.write(projectFile, new LocalizationProject(
                1,
                "original.mod",
                "original.mod.translation",
                "Original Mod Translation",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "/welcome",
                        "Hello %s",
                        ""))));

        Path request = temporary.resolve("request.json");
        new AiTranslationExchangeService().exportPackage(
                request, service.read(projectFile), "Original Mod", "en", "fr");
        String requestText = Files.readString(request, StandardCharsets.UTF_8);
        Path responseFile = temporary.resolve("response.json");
        Files.writeString(
                responseFile,
                requestText.replace("\"translation\" : \"\"", "\"translation\" : \"Bonjour %s\""),
                StandardCharsets.UTF_8);

        int exitCode = new CommandLine(new Main()).execute(
                "project", "import-ai-response", projectFile.toString(), responseFile.toString());

        assertThat(exitCode).isZero();
        LocalizationProject updated = service.read(projectFile);
        assertThat(updated.entries().getFirst().translatedText()).isEqualTo("Bonjour %s");
        assertThat(updated.entries().getFirst().provenance())
                .isEqualTo(TranslationProvenance.AI_TRANSLATED);
    }

    @Test
    void rejectsResponseForADifferentProject() throws Exception {
        LocalizationProjectService service = new LocalizationProjectService();
        Path projectFile = temporary.resolve("project.ssmt.json");
        service.write(projectFile, new LocalizationProject(
                1,
                "original.mod",
                "original.mod.translation",
                "Original Mod Translation",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "/welcome",
                        "Hello %s",
                        ""))));
        Path responseFile = temporary.resolve("response.json");
        Files.writeString(responseFile, """
                {
                  "schemaVersion": 1,
                  "sourceModId": "a-different-mod",
                  "entries": []
                }
                """, StandardCharsets.UTF_8);

        int exitCode = new CommandLine(new Main()).execute(
                "project", "import-ai-response", projectFile.toString(), responseFile.toString());

        assertThat(exitCode).isEqualTo(1);
    }
}
