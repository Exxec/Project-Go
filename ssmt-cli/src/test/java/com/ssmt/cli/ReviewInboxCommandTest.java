package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ReviewInboxCommandTest {
    @TempDir Path directory;

    private int run(StringWriter out, String... args) {
        var command = new CommandLine(new ReviewInboxCommand());
        command.setOut(new PrintWriter(out, true));
        command.setErr(new PrintWriter(out, true));
        return command.execute(args);
    }

    @Test void listIsReadOnlyAndRecordWritesOnlyOnExplicitRequest() throws Exception {
        Files.writeString(directory.resolve("checklist.json"), """
                {"rows":[{"scenario":"A","expected":"e","automated":"AUTOMATED_PASS","observed_by_program":"o",
                 "screenshots":[]}]}
                """);
        var out = new StringWriter();

        assertThat(run(out, "list", "--dir", directory.toString())).isZero();
        assertThat(out.toString()).contains("1. [UNREVIEWED] A").contains("1 rows need review");
        assertThat(directory.resolve("owner-review.json")).doesNotExist();

        assertThat(run(out, "record", "--dir", directory.toString(), "--row", "1", "--status", "FAIL")).isEqualTo(1);
        assertThat(directory.resolve("owner-review.json")).doesNotExist();

        assertThat(run(out, "record", "--dir", directory.toString(), "--row", "1", "--status", "PASS")).isZero();
        assertThat(directory.resolve("owner-review.json")).isRegularFile();
        var after = new StringWriter();
        run(after, "list", "--dir", directory.toString());
        assertThat(after.toString()).contains("0 rows need review");
    }
}
