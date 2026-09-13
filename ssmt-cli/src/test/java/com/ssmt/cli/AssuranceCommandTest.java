package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AssuranceCommandTest {
    @TempDir Path root;

    @Test void pendingTemplateIsBoundToSourceAndCannotClaimReady() throws Exception {
        Path candidate = Files.createDirectory(root.resolve("candidate"));
        Files.writeString(candidate.resolve("mod_info.json"), "{\"id\":\"candidate\"}");
        var before = new com.ssmt.scanner.CandidateInventory().capture(candidate);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assurance", "--candidate", candidate.toString(), "--template")).isZero();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var ledger = mapper.readValue(output.toString(), com.ssmt.project.AssuranceLedgerReader.Ledger.class);
        assertThat(ledger.results()).hasSize(com.ssmt.project.AssuranceSummary.Gate.values().length)
                .allMatch(result -> result.disposition() == com.ssmt.project.AssuranceSummary.Disposition.NOT_TESTED);
        Path file = root.resolve("ledger.json");
        Files.writeString(file, output.toString());
        StringWriter checked = new StringWriter();
        var inspect = new CommandLine(new Main());
        inspect.setOut(new PrintWriter(checked));
        assertThat(inspect.execute("assurance", file.toString(), "--candidate", candidate.toString(), "--json"))
                .isEqualTo(1);
        assertThat(checked.toString()).contains("ESCALATION_REQUIRED", "\"evidenceSemanticsVerified\":false");
        assertThat(new com.ssmt.scanner.CandidateInventory().capture(candidate)).isEqualTo(before);
        Files.writeString(candidate.resolve("added.txt"), "changed candidate");
        assertThat(inspect.execute("assurance", file.toString(), "--candidate", candidate.toString(), "--json"))
                .isEqualTo(1);
    }

    @Test void requiresExplicitLedgerOrTemplate() throws Exception {
        Files.writeString(root.resolve("mod_info.json"), "{\"id\":\"candidate\"}");
        assertThat(new CommandLine(new Main()).execute("assurance", "--candidate", root.toString())).isEqualTo(1);
    }
}
