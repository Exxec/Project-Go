package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.project.BridgeForgeShippedHash;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AssuranceImportRunCommandTest {
    @TempDir Path root;

    private static String record(String shipped) {
        return "{\"schema_version\":1,\"mode\":\"LIVE_RUN_RECORD\",\"test_id\":\"CLI-1\",\"manifest_id\":\"m1\","
                + "\"members\":[{\"workspace\":\"W\",\"mod_id\":\"cli\",\"version\":\"1\",\"shipped_sha256\":\""
                + shipped + "\",\"verdict\":\"PASS\",\"provenance\":\"BOUND\"}],"
                + "\"triage\":{\"FATAL\":0,\"MOD-ERROR\":0},\"jvm_crashes\":[],"
                + "\"milestones\":{\"main_menu_reached\":true,\"campaign_loads\":[\"s\"],\"finished_saving_count\":0},"
                + "\"environment\":{\"starsector_build\":\"RC8\",\"java_executable\":\"java\",\"java_version\":\"25\","
                + "\"process_exit_code\":0}}";
    }

    @Test void importRunWritesTheLedgerForMatchingBytesAndLeavesGatesPendingOtherwise() throws Exception {
        Path candidate = Files.createDirectory(root.resolve("candidate"));
        Files.writeString(candidate.resolve("mod_info.json"), "{\"id\":\"cli\",\"name\":\"Cli\",\"version\":\"1\"}");
        Path matching = root.resolve("_live/CLI-1.json");
        Files.createDirectories(matching.getParent());
        Files.writeString(matching, record(BridgeForgeShippedHash.of(candidate)));
        Path ledger = root.resolve("out/ledger.json");
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assurance", "--candidate", candidate.toString(), "--import-run",
                matching.toString(), "--ledger-out", ledger.toString())).isZero();
        assertThat(output.toString()).contains("AUTOMATED_BOOT PASS").contains("CAMPAIGN PASS")
                .contains("SAVE_RELOAD NOT_TESTED");
        assertThat(ledger).isRegularFile();
        assertThat(ledger.resolveSibling("runtime-CLI-1.json")).isRegularFile();

        Path stale = root.resolve("_live/CLI-2.json");
        Files.writeString(stale, record("0".repeat(64)).replace("CLI-1", "CLI-2"));
        StringWriter second = new StringWriter();
        var again = new CommandLine(new Main());
        again.setOut(new PrintWriter(second));
        assertThat(again.execute("assurance", "--candidate", candidate.toString(), "--import-run", stale.toString(),
                "--ledger-out", root.resolve("out/other.json").toString())).isZero();
        assertThat(second.toString()).contains("AUTOMATED_BOOT NOT_TESTED").doesNotContain("PASS");
        assertThat(again.execute("assurance", "--candidate", candidate.toString(), "--import-run", stale.toString()))
                .isEqualTo(1);
    }
}
