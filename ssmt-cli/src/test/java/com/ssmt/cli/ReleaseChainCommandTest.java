package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ReleaseChainCommandTest {
    @TempDir Path directory;

    @Test void matchingAssetDoesNotInferNativeOrLiveValidation() throws Exception {
        Path record = directory.resolve("release-source.txt");
        Files.writeString(record, "version=0.8.0\ncommit=" + "a".repeat(40) + "\n");
        Path archive = directory.resolve("candidate.zip");
        Path downloaded = directory.resolve("downloaded.zip");
        Files.writeString(archive, "bytes");
        Files.writeString(downloaded, "bytes");
        var output = new StringWriter();
        var command = new CommandLine(new Main());
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("release-chain", "--source-record", record.toString(),
                "--archive", archive.toString(), "--downloaded-asset", downloaded.toString())).isZero();
        var report = new ObjectMapper().readTree(output.toString());
        assertThat(report.path("downloadedAsset").path("state").asText()).isEqualTo("ASSET_BYTES_MATCH");
        assertThat(report.path("gates").path("publication").asText()).isEqualTo("UNKNOWN");
        assertThat(report.path("gates").path("native").asText()).isEqualTo("UNKNOWN");
        assertThat(report.path("gates").path("live").asText()).isEqualTo("UNKNOWN");
        Files.writeString(downloaded, "other");
        assertThat(command.execute("release-chain", "--source-record", record.toString(),
                "--archive", archive.toString(), "--downloaded-asset", downloaded.toString())).isEqualTo(1);
    }
}
