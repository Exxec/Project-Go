package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** These records are synthetic producer fixtures, never evidence of a game run. */
class RuntimeContextConformanceTest {
    private static final Path FIXTURE = Path.of("../fixtures/runtime-context");
    @TempDir Path temporary;

    @Test void explicitlyObservedZeroExitReachesOnlyTheSyntheticRecordedScenarios() throws Exception {
        var imported = new RunRecordImport().read(FIXTURE.resolve("observed-zero.synthetic.json"), FIXTURE.resolve("candidate"));
        assertThat(imported.results()).filteredOn(result -> result.gate() == AssuranceSummary.Gate.AUTOMATED_BOOT
                || result.gate() == AssuranceSummary.Gate.CAMPAIGN)
                .allMatch(result -> result.disposition() == AssuranceSummary.Disposition.PASS);
        assertThat(imported.results()).filteredOn(result -> result.gate() == AssuranceSummary.Gate.SAVE_RELOAD)
                .allMatch(result -> result.disposition() == AssuranceSummary.Disposition.NOT_TESTED);
        Path ledger = temporary.resolve("ledger.json");
        new RunRecordImport().writeLedger(imported, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, imported.candidateSha256()).results()).hasSize(13);
    }

    @Test void anUnknownExitFromTheRealProducerShapeNeverBecomesSuccess() throws Exception {
        var imported = new RunRecordImport().read(FIXTURE.resolve("unknown-exit.synthetic.json"), FIXTURE.resolve("candidate"));
        assertThat(imported.results()).allMatch(result -> result.disposition() == AssuranceSummary.Disposition.NOT_TESTED);
        assertThat(imported.notes().toString()).contains("process_exit_code").contains("nothing is guessed");
    }

    @Test void nonzeroAndMalformedExitStatusesCannotPass() throws Exception {
        var mapper = new ObjectMapper();
        for (boolean malformed : new boolean[] {false, true}) {
            ObjectNode record = (ObjectNode) mapper.readTree(FIXTURE.resolve("observed-zero.synthetic.json").toFile());
            if (malformed) {
                record.put("process_exit_code", false);
            } else {
                record.put("process_exit_code", 7);
            }
            Path file = temporary.resolve(malformed ? "malformed.json" : "failed.json");
            mapper.writeValue(file.toFile(), record);
            var imported = new RunRecordImport().read(file, FIXTURE.resolve("candidate"));
            assertThat(imported.results()).noneMatch(result -> result.disposition() == AssuranceSummary.Disposition.PASS);
        }
    }
}
