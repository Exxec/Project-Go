package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class OfflineTranslateCommandTest {
    @TempDir Path temporary;

    @Test
    void returnsApprovedGlossaryHitWithoutStartingProviders() throws Exception {
        Path database = temporary.resolve("glossary.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.create(new TranslationDraft(
                    "Hello", "en", "es", "Hola", "",
                    TranslationProvenance.HUMAN_EDITED));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine command = new CommandLine(new Main());
        command.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        command.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));

        int exit = command.execute("offline-translate", "Hello",
                "--source-language", "en", "--target-language", "es",
                "--memory", database.toString());

        assertThat(exit).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("Hola" + System.lineSeparator());
        assertThat(error.toString(StandardCharsets.UTF_8)).contains("APPROVED_GLOSSARY");
    }

    @Test
    void printsPlainLanguageDiagnosticWhenBothLocalProvidersAreUnavailable() throws Exception {
        Path database = temporary.resolve("empty.db");
        // Created only so a valid, empty catalog exists; no glossary hit is seeded.
        SqliteTranslationMemory.open(database).close();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine command = new CommandLine(new Main());
        command.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));

        int exit = command.execute("offline-translate", "Text with no glossary entry",
                "--source-language", "en", "--target-language", "es",
                "--memory", database.toString());

        assertThat(exit).isEqualTo(1);
        String printed = error.toString(StandardCharsets.UTF_8);
        assertThat(printed)
                .startsWith("Offline translation failed:")
                .contains("check the inputs and try again");
    }
}
