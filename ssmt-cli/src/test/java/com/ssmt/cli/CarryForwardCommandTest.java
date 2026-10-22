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

class CarryForwardCommandTest {
    @TempDir Path temporary;

    private static ProjectEntry entry(String key, String source, String translation, TranslationProvenance by) {
        return new ProjectEntry(Path.of("data/strings/strings.json"), key, source, translation, by);
    }

    @Test
    void carriesWritesReportsAndLeavesBothInputsUntouched() throws Exception {
        LocalizationProjectService service = new LocalizationProjectService();
        Path from = temporary.resolve("old.ssmt.json");
        Path to = temporary.resolve("new.ssmt.json");
        service.write(from, new LocalizationProject(1, "m", "m.tr", "M", List.of(
                entry("/a", "你好", "Hello", TranslationProvenance.HUMAN_EDITED),
                entry("/b", "旧", "Old", TranslationProvenance.HUMAN_EDITED))));
        service.write(to, new LocalizationProject(1, "m", "m.tr", "M", List.of(
                entry("/a", "你好", "", TranslationProvenance.MANUAL_IMPORT),
                entry("/b", "新", "", TranslationProvenance.MANUAL_IMPORT))));
        byte[] oldBytes = Files.readAllBytes(from);
        byte[] newBytes = Files.readAllBytes(to);
        Path out = temporary.resolve("carried.ssmt.json");
        Path report = temporary.resolve("report.json");

        int exit = new CommandLine(new Main()).execute("project", "carry-forward", "--from", from.toString(),
                "--to", to.toString(), "--out", out.toString(), "--report", report.toString());

        assertThat(exit).isZero();
        assertThat(Files.readAllBytes(from)).isEqualTo(oldBytes);
        assertThat(Files.readAllBytes(to)).isEqualTo(newBytes);
        LocalizationProject carried = service.read(out);
        assertThat(carried.entries()).extracting(ProjectEntry::translatedText).containsExactly("Hello", "");
        JsonNode json = new ObjectMapper().readTree(report.toFile());
        assertThat(json.path("counts").path("CARRIED").asInt()).isEqualTo(1);
        assertThat(json.path("counts").path("CHANGED").asInt()).isEqualTo(1);
        assertThat(Files.readString(temporary.resolve("report.json.txt"))).contains("CHANGED");

        int again = new CommandLine(new Main()).execute("project", "carry-forward", "--from", from.toString(),
                "--to", to.toString(), "--out", out.toString(), "--report", report.toString());
        assertThat(again).isEqualTo(1);
    }
}
