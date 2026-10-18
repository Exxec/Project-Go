package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic file shaped like a custom dialogue CSV (unknown schema, comment row, option lists). */
class CustomOptionCsvTest {
    private static final String FILE = "data/campaign/custom_dialogue.csv";
    private static final String OPTIONS = "go:前进|stay:留下|$leave:离开";
    @TempDir Path temporary;

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/campaign"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"opt\",\"name\":\"Opt\",\"version\":\"1\"}");
        Files.writeString(mod.resolve(FILE), "id,text,options\n# WIP\nd1,\"你好 $playerName, 选 %s\",\""
                + OPTIONS + "\"\nd2,普通|文本,\n");
        return mod;
    }

    private ObjectNode translate(Path mod, String text, String options) throws Exception {
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        for (JsonNode entry : document.path("entries")) {
            String id = entry.path("id").asText();
            String value = id.endsWith(":text") && id.contains("d1") ? text
                    : id.endsWith(":options") ? options : "Plain|text";
            ((ObjectNode) entry).put("translation", value);
        }
        return document;
    }

    @Test void exportsTextAndOptionCellsSeparatelyAndSkipsTheCommentRow() throws Exception {
        var document = new BridgeForgeTranslationService().export(mod());
        assertThat(document.path("entries")).extracting(entry -> entry.path("id").asText()).containsExactly(
                "csv:" + FILE + "#d1:text", "csv:" + FILE + "#d1:options", "csv:" + FILE + "#d2:text");
    }

    @Test void appliesWhenOptionIdsSeparatorsAndPlaceholdersSurvive() throws Exception {
        Path mod = mod();
        var document = translate(mod, "Hello $playerName, pick %s", "go:Advance|stay:Stay|$leave:Leave");
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        assertThat(report.path("problems")).isEmpty();
        assertThat(Files.readString(temporary.resolve("out").resolve(FILE)))
                .contains("go:Advance|stay:Stay|$leave:Leave").contains("Plain|text");
    }

    @Test void refusesAnOptionCellWhoseIdOrSeparatorChanged() throws Exception {
        for (String bad : new String[] {"goes:Advance|stay:Stay|$leave:Leave", "go:Advance, stay:Stay|$leave:Leave",
            "go:Advance|stay:Stay"}) {
            Path mod = temporary.resolve("m" + bad.length());
            Files.createDirectories(mod);
            Files.copy(mod().resolve("mod_info.json"), mod.resolve("mod_info.json"));
            Files.createDirectories(mod.resolve("data/campaign"));
            Files.copy(temporary.resolve("mod").resolve(FILE), mod.resolve(FILE));
            var document = translate(mod, "Hello $playerName, pick %s", bad);
            var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("o" + bad.length()), false);
            assertThat(report.path("problems").toString()).contains("differ");
            assertThat(report.path("leftover").path("by_reason").path("REJECTED").asInt()).isEqualTo(1);
        }
    }

    @Test void refusesATextCellThatDropsTheVariableOrFormatSlot() throws Exception {
        Path mod = mod();
        var document = translate(mod, "Hello, pick", "go:Advance|stay:Stay|$leave:Leave");
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        assertThat(report.path("problems").toString()).contains("placeholders differ");
    }
}
