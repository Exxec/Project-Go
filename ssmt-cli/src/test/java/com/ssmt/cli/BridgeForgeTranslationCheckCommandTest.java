package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BridgeForgeTranslationCheckCommandTest {
    @TempDir Path temporary;

    private Path modWithUnreadableFileOnly() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"chk\",\"name\":\"Chk\",\"version\":\"1\"}");
        Files.write(mod.resolve("data/config/bad.json"), new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
        return mod;
    }

    @Test void checkFailsOnUnreadableInputsUntilTheyAreAcknowledged() throws Exception {
        Path mod = modWithUnreadableFileOnly();
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("check", mod.toString())).isEqualTo(2);
        assertThat(new CommandLine(new BridgeForgeTranslationCommand())
                .execute("check", mod.toString(), "--accept-unreadable")).isZero();
        assertThat(new CommandLine(new BridgeForgeTranslationCommand())
                .execute("export", mod.toString(), "--out", temporary.resolve("x.json").toString(),
                        "--accept-unreadable")).isEqualTo(1);
    }

    @Test void projectCreateAcceptsUnreadableInputsOnlyWhenAcknowledged() throws Exception {
        Path mod = modWithUnreadableFileOnly();
        Files.writeString(mod.resolve("data/good.csv"), "id,name\nx,中文\n");
        Path project = temporary.resolve("project.json");
        assertThat(new CommandLine(new ProjectCommand()).execute("create", mod.toString(), project.toString(),
                "--patch-id", "p", "--patch-name", "P")).isZero();
        assertThat(Files.readString(project)).contains("data/config/bad.json").doesNotContain("unreadable_acknowledged");
        Path accepted = temporary.resolve("accepted.json");
        assertThat(new CommandLine(new ProjectCommand()).execute("create", mod.toString(), accepted.toString(),
                "--patch-id", "p", "--patch-name", "P", "--accept-unreadable")).isZero();
        assertThat(Files.readString(accepted)).contains("unreadable_acknowledged");
    }

    @Test void checkJsonGivesEachLeftoverItemItsReason() throws Exception {
        Path mod = modWithUnreadableFileOnly();
        Files.writeString(mod.resolve("data/good.csv"), "id,name\nx,中文\n");
        Files.writeString(mod.resolve("readme.md"), "中文");
        Path report = temporary.resolve("leftover.json");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand())
                .execute("check", mod.toString(), "--json", report.toString())).isEqualTo(2);
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(report.toFile());
        assertThat(tree.path("by_reason").path("UNTRANSLATED").asInt()).isEqualTo(1);
        assertThat(tree.path("by_reason").path("UNREADABLE").asInt()).isEqualTo(1);
        assertThat(tree.path("by_reason").path("UNSUPPORTED_TYPE").asInt()).isEqualTo(1);
    }
}
