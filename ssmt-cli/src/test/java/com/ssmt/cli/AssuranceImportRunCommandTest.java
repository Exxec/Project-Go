package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Uses the run record BridgeForge itself wrote for a synthetic tree (see ssmt-project's bridgeforge-r1 fixture). */
class AssuranceImportRunCommandTest {
    private static final Path FIXTURE = Path.of("../ssmt-project/src/test/resources/bridgeforge-r1");
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path root;

    private Path record(boolean withLaunchFacts) throws Exception {
        ObjectNode record = (ObjectNode) JSON.readTree(FIXTURE.resolve("R-FIX.json").toFile());
        if (withLaunchFacts) {
            ((ObjectNode) record.path("manifest")).put("java_executable", "java.exe").put("java_version", "25");
            record.put("process_exit_code", 0);
        }
        Path file = root.resolve("_live/R-FIX.json");
        Files.createDirectories(java.util.Objects.requireNonNull(file.getParent()));
        JSON.writeValue(file.toFile(), record);
        return file;
    }

    private int run(StringWriter output, String... arguments) {
        var command = new CommandLine(new Main());
        command.setOut(new PrintWriter(output));
        return command.execute(arguments);
    }

    @Test void importRunReportsWhatTheRealRecordLacksAndPassesOnceTheLaunchFactsExist() throws Exception {
        Path candidate = FIXTURE.resolve("candidate");
        StringWriter pending = new StringWriter();
        Path ledger = root.resolve("out/ledger.json");
        assertThat(run(pending, "assurance", "--candidate", candidate.toString(), "--import-run",
                record(false).toString(), "--ledger-out", ledger.toString())).isZero();
        assertThat(pending.toString()).contains("AUTOMATED_BOOT NOT_TESTED").contains("manifest.java_version")
                .contains("process_exit_code").doesNotContain("PASS");

        StringWriter passing = new StringWriter();
        Path second = root.resolve("out/second.json");
        assertThat(run(passing, "assurance", "--candidate", candidate.toString(), "--import-run",
                record(true).toString(), "--ledger-out", second.toString())).isZero();
        assertThat(passing.toString()).contains("AUTOMATED_BOOT PASS").contains("CAMPAIGN PASS")
                .contains("SAVE_RELOAD NOT_TESTED");
        assertThat(second.resolveSibling("runtime-R-FIX.json")).isRegularFile();
        assertThat(run(new StringWriter(), "assurance", "--candidate", candidate.toString(), "--import-run",
                record(true).toString())).isEqualTo(1);
    }
}
