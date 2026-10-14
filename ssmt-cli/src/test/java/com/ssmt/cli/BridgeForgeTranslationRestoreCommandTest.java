package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.BridgeForgeTranslationService;
import com.ssmt.project.TranslationSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BridgeForgeTranslationRestoreCommandTest {
    @TempDir Path temporary;

    @Test void inPlaceApplyThenRestoreReturnsTheOriginalBytes() throws Exception {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"snap\",\"name\":\"Snap\",\"version\":\"1\"}");
        byte[] original = "id,name\nx,中文\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(mod.resolve("data/a.csv"), original);
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        for (var entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", "English");
        }
        Path translated = temporary.resolve("translated.json");
        new ObjectMapper().writeValue(translated.toFile(), document);

        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("apply", mod.toString(),
                "--document", translated.toString(), "--in-place")).isZero();
        assertThat(Files.readAllBytes(mod.resolve("data/a.csv"))).isNotEqualTo(original);
        String stamp = TranslationSnapshot.list(mod).getFirst();
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("restore", mod.toString(), stamp))
                .isZero();
        assertThat(Files.readAllBytes(mod.resolve("data/a.csv"))).isEqualTo(original);
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("restore", mod.toString(), "missing"))
                .isEqualTo(1);
    }
}
