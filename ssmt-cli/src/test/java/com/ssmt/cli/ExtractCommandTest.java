package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ExtractCommandTest {

    @Test
    void extractsStandardFilesFromValidMod(@TempDir Path modRoot) throws Exception {
        Files.writeString(modRoot.resolve("mod_info.json"), """
                {"id":"test_mod","name":"Test Mod"}
                """);
        Path strings = modRoot.resolve("data/strings");
        Files.createDirectories(strings);
        Files.writeString(strings.resolve("descriptions.csv"), """
                id,type,text1,text2,text3,text4
                test,WEAPON,Localizable text,,,
                """);

        int exitCode = new CommandLine(new Main())
                .execute("extract", modRoot.toString());

        assertThat(exitCode).isZero();
    }

    @Test
    void invalidModDirectoryFails(@TempDir Path temporaryDirectory) {
        int exitCode = new CommandLine(new Main())
                .execute("extract", temporaryDirectory.resolve("missing").toString());

        assertThat(exitCode).isEqualTo(1);
    }
}
