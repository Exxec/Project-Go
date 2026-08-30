package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ScanCommandTest {

    @Test
    void validDirectoryExitsSuccessfully(@TempDir Path temporaryDirectory) throws Exception {
        Path modDirectory = Files.createDirectory(temporaryDirectory.resolve("test-mod"));
        Files.writeString(modDirectory.resolve("mod_info.json"), """
                {
                  "id": "test_mod",
                  "name": "Test Mod",
                  "version": "1.0.0"
                }
                """);

        int exitCode = new CommandLine(new Main())
                .execute("scan", temporaryDirectory.toString());

        assertThat(exitCode).isZero();
    }

    @Test
    void nonexistentDirectoryExitsWithFailure(@TempDir Path temporaryDirectory) {
        int exitCode = new CommandLine(new Main())
                .execute("scan", temporaryDirectory.resolve("missing").toString());

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void missingDirectoryArgumentIsAUsageError() {
        int exitCode = new CommandLine(new Main()).execute("scan");

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
    }
}
