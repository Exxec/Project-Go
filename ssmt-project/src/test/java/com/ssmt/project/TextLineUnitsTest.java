package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic mission and strings text; no mod files. */
class TextLineUnitsTest {
    private static final String MISSION = "data/missions/probe/mission_text.txt";
    @TempDir Path temporary;

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/missions/probe"));
        Files.createDirectories(mod.resolve("data/strings"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"txt\",\"name\":\"Txt\",\"version\":\"1\"}");
        Files.writeString(mod.resolve(MISSION),
                "Briefing\r\n  任务目标  \r\n\r\n中文 %s\r\nend", StandardCharsets.UTF_8);
        Files.writeString(mod.resolve("data/strings/tips.txt"), "﻿提示\n");
        Files.writeString(mod.resolve("notes.txt"), "中文注释");
        return mod;
    }

    private static ObjectNode translated(ObjectNode document, String text) {
        for (JsonNode entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", text);
        }
        return document;
    }

    @Test void exportsOneUnitPerChineseLineWithStableIdsAndOnlyOnCoveredPaths() throws Exception {
        var document = new BridgeForgeTranslationService().export(mod());
        assertThat(document.path("entries")).extracting(entry -> entry.path("id").asText()).containsExactly(
                "text:data/missions/probe/mission_text.txt@2", "text:data/missions/probe/mission_text.txt@4",
                "text:data/strings/tips.txt@1");
        var first = document.path("entries").get(0);
        assertThat(first.path("kind").asText()).isEqualTo("text-line");
        assertThat(first.path("source").asText()).isEqualTo("任务目标");
        assertThat(first.path("context").path("line").asInt()).isEqualTo(2);
        assertThat(document.path("file_hashes").has("notes.txt")).isFalse();
    }

    @Test void applyKeepsIndentationLineEndingsAndRoundTripsThroughTheCleanCheck() throws Exception {
        Path mod = mod();
        var document = new BridgeForgeTranslationService().export(mod);
        for (JsonNode entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", entry.path("source").asText().contains("%s") ? "Hello %s" : "Goal");
        }
        Path out = temporary.resolve("out");
        var report = new BridgeForgeTranslationApply().apply(mod, document, out, false);
        assertThat(report.path("applied").path("text").asInt()).isEqualTo(3);
        assertThat(Files.readString(out.resolve(MISSION)))
                .isEqualTo("Briefing\r\n  Goal  \r\n\r\nHello %s\r\nend");
        assertThat(Files.readString(out.resolve("data/strings/tips.txt"), StandardCharsets.UTF_8)).isEqualTo("﻿Goal\n");
        assertThat(new BridgeForgeTranslationService().export(out).path("entry_count").asInt()).isZero();
        assertThat(report.path("leftover").path("by_reason").path("UNSUPPORTED_TYPE").asInt()).isEqualTo(1);
    }

    @Test void legacyGb18030MissionTextIsReadAndWrittenBackInTheSameEncoding() throws Exception {
        Path mod = mod();
        Charset legacy = Charset.forName("GB18030");
        Files.write(mod.resolve(MISSION), "任务\r\nEnd".getBytes(legacy));
        var document = new BridgeForgeTranslationService().export(mod);
        assertThat(document.path("unreadable")).isEmpty();
        var entry = document.path("entries").get(0);
        assertThat(entry.path("source").asText()).isEqualTo("任务");
        translated(document, "Mission");
        var out = temporary.resolve("out");
        new BridgeForgeTranslationApply().apply(mod, document, out, false);
        assertThat(Files.readAllBytes(out.resolve(MISSION))).isEqualTo("Mission\r\nEnd".getBytes(legacy));
    }

    @Test void aTranslationWithALineBreakIsRefusedForThatLine() throws Exception {
        Path mod = mod();
        var document = translated(new BridgeForgeTranslationService().export(mod), "Two\nlines");
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        assertThat(report.path("problems")).hasSize(3);
        assertThat(report.path("leftover").path("by_reason").path("REJECTED").asInt()).isEqualTo(3);
    }
}
