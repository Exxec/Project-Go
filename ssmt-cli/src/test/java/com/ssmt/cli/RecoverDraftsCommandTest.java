package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class RecoverDraftsCommandTest {
    @TempDir Path temporary;

    @Test
    void planExportAndStatusRunAgainstASyntheticPacketAndRejectAMissingPacket() throws Exception {
        Path packet = temporary.resolve("packet");
        Files.createDirectories(packet);
        Files.writeString(packet.resolve("review.json"), "{\"entries\":[{\"id\":\"a\",\"role\":\"TEXT\","
                + "\"source\":\"x\",\"current_target\":\"\",\"retained_target\":\"\"}]}", StandardCharsets.UTF_8);
        Path workspace = temporary.resolve("work");

        assertThat(run("plan", packet, workspace)).isZero();
        assertThat(run("export", packet, workspace)).isZero();
        assertThat(new CommandLine(new Main()).execute("recover-drafts", "status", "--workspace",
                workspace.toString())).isZero();
        assertThat(workspace.resolve("batches/batch-0001.json")).exists();
        assertThat(run("plan", temporary.resolve("absent"), temporary.resolve("w2"))).isEqualTo(1);
    }

    private static int run(String operation, Path packet, Path workspace) {
        return new CommandLine(new Main()).execute("recover-drafts", operation, "--packet", packet.toString(),
                "--workspace", workspace.toString(), "--batch-size", "10");
    }
}
