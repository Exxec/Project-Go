package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StringDiffCommandTest {
    @TempDir Path temporary;

    private Path root(String name, String json) throws Exception {
        Path file = temporary.resolve(name).resolve("data/strings/strings.json");
        Files.createDirectories(java.util.Objects.requireNonNull(file.getParent()));
        Files.writeString(file, json);
        return temporary.resolve(name);
    }

    @Test
    void writesCountsReportAndSummaryAndRefusesToOverwrite() throws Exception {
        Path oldRoot = root("old", "{\"g\":{\"a\":\"One\",\"b\":\"Two\"}}");
        Path newRoot = root("new", "{\"g\":{\"a\":\"One\",\"b\":\"Two!\",\"c\":\"Three\"}}");
        Path pack = temporary.resolve("pack.ssmt.json");
        new LocalizationProjectService().write(pack, new LocalizationProject(1, "v", "v.tr", "V", List.of(
                new ProjectEntry(Path.of("data/strings/strings.json"), "json:/g/a", "One", "Eins",
                        TranslationProvenance.HUMAN_EDITED))));
        Path report = temporary.resolve("diff.json");
        String[] arguments = {"string-diff", oldRoot.toString(), newRoot.toString(), "--pack", pack.toString(),
            "--report", report.toString()};

        int exit = new CommandLine(new Main()).execute(arguments);

        assertThat(exit).isZero();
        JsonNode json = new ObjectMapper().readTree(report.toFile());
        assertThat(json.path("counts").path("ADDED").asInt()).isEqualTo(1);
        assertThat(json.path("counts").path("CHANGED").asInt()).isEqualTo(1);
        assertThat(json.path("carryCounts").path("CARRIED_OVER").asInt()).isEqualTo(1);
        assertThat(json.path("carryCounts").path("NEEDS_TRANSLATION").asInt()).isEqualTo(2);
        assertThat(Files.readString(temporary.resolve("diff.json.txt"))).contains("CARRIED_OVER");
        assertThat(Files.readString(report)).doesNotContain("Eins", "Three");
        assertThat(new CommandLine(new Main()).execute(arguments)).isEqualTo(1);
    }

    @Test
    void failsCleanlyWhenARootIsMissing() {
        Path report = temporary.resolve("diff.json");

        int exit = new CommandLine(new Main()).execute("string-diff", temporary.resolve("nope").toString(),
                temporary.resolve("nope2").toString(), "--report", report.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(report).doesNotExist();
    }
}
