package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeftoverClassifierTest {
    @TempDir Path temporary;

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"left\",\"name\":\"Left\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/a.csv"),
                "id,name\ndone,已翻译\nmissing,未翻译\nbad,坐标 %s\n");
        Files.write(mod.resolve("data/config/bad.json"), new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
        Files.writeString(mod.resolve("notes.md"), "中文说明");
        return mod;
    }

    private static String reason(JsonNode leftover, String file) {
        for (JsonNode item : leftover.path("items")) {
            if (item.path("file").asText().equals(file) && !item.path("id").asText().contains("done")) {
                return item.path("reason").asText();
            }
        }
        return "";
    }

    @Test void applyReportGivesEveryLeftoverItemItsReason() throws Exception {
        Path mod = mod();
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        for (JsonNode entry : document.path("entries")) {
            String id = entry.path("id").asText();
            ((ObjectNode) entry).put("translation", id.contains("done") ? "Translated"
                    : id.contains("bad") ? "Coordinates %d" : "");
        }
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        var leftover = report.path("leftover");
        assertThat(leftover.path("by_reason").path("UNTRANSLATED").asInt()).isEqualTo(1);
        assertThat(leftover.path("by_reason").path("REJECTED").asInt()).isEqualTo(1);
        assertThat(leftover.path("by_reason").path("UNREADABLE").asInt()).isEqualTo(1);
        assertThat(leftover.path("by_reason").path("UNSUPPORTED_TYPE").asInt()).isEqualTo(1);
        assertThat(leftover.path("by_reason").path("PROTECTED_IDENTIFIER").asInt()).isZero();
        assertThat(reason(leftover, "notes.md")).isEqualTo("UNSUPPORTED_TYPE");
        assertThat(reason(leftover, "data/config/bad.json")).isEqualTo("UNREADABLE");
        assertThat(leftover.path("total").asInt()).isEqualTo(4);
        assertThat(report.path("status").asText()).isEqualTo("PARTIAL");
    }

    @Test void aCleanModHasNoLeftoverSection() throws Exception {
        Path mod = temporary.resolve("clean");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"clean\",\"name\":\"Clean\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/a.csv"), "id,name\nx,中文\n");
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        ((ObjectNode) document.path("entries").get(0)).put("translation", "English");
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        assertThat(report.has("leftover")).isFalse();
        assertThat(report.path("status").asText()).isEqualTo("OK");
    }
}
