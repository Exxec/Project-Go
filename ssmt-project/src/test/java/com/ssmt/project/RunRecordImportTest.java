package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.AssuranceSummary.Disposition;
import com.ssmt.project.AssuranceSummary.Gate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic candidate and synthetic run records; no BridgeForge data or game files. */
class RunRecordImportTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    private Path candidate() throws IOException {
        Path mod = temporary.resolve("candidate");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"live\",\"name\":\"Live\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/a.csv"), "id,name\nx,English\n");
        return mod;
    }

    /** A bound, fully milestoned record for the given shipped hash; callers adjust it. */
    private ObjectNode record(String shipped, Consumer<ObjectNode> adjust) throws Exception {
        ObjectNode record = (ObjectNode) JSON.readTree("""
                {"schema_version":1,"mode":"LIVE_RUN_RECORD","test_id":"RUN-1","date":"2026-10-18",
                 "manifest_id":"manifest-abc",
                 "members":[{"workspace":"Live","mod_id":"live","version":"1","verdict":"PASS","provenance":"BOUND"}],
                 "triage":{"FATAL":0,"MOD-ERROR":0,"KNOWN-NOISE":2,"OTHER":0},"jvm_crashes":[],
                 "milestones":{"main_menu_reached":true,"campaign_loads":["saves/a"],"finished_saving_count":1,
                               "reload_after_save":true},
                 "environment":{"starsector_build":"0.98a-RC8","java_executable":"java.exe","java_version":"25",
                                "process_exit_code":0}}
                """);
        ((ObjectNode) record.path("members").get(0)).put("shipped_sha256", shipped);
        adjust.accept(record);
        return record;
    }

    private Path write(ObjectNode record) throws IOException {
        Path file = temporary.resolve("_live/" + record.path("test_id").asText("x") + ".json");
        Files.createDirectories(java.util.Objects.requireNonNull(file.getParent()));
        JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), record);
        return file;
    }

    private static Disposition gate(RunRecordImport.Imported imported, Gate gate) {
        return imported.results().stream().filter(item -> item.gate() == gate).findFirst().orElseThrow()
                .disposition();
    }

    @Test void aBoundRecordForTheSameBytesSetsTheThreeGatesAndTheLedgerVerifies() throws Exception {
        Path mod = candidate();
        String shipped = BridgeForgeShippedHash.of(mod);
        var service = new RunRecordImport();
        var imported = service.read(write(record(shipped, record -> { })), mod);
        assertThat(gate(imported, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.CAMPAIGN)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.SAVE_RELOAD)).isEqualTo(Disposition.PASS);
        Path ledger = temporary.resolve("ledger/ledger.json");
        service.writeLedger(imported, ledger);
        var summary = new AssuranceLedgerReader().read(ledger, imported.candidateSha256());
        assertThat(summary.results()).filteredOn(item -> item.gate() == Gate.COMBAT).singleElement()
                .satisfies(item -> assertThat(item.disposition()).isEqualTo(Disposition.NOT_TESTED));
        assertThat(ledger.resolveSibling("run-record-RUN-1.json")).isRegularFile();
        // Merging again keeps the ledger valid and does not duplicate gates.
        service.writeLedger(imported, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, imported.candidateSha256()).results()).hasSize(13);
    }

    @Test void bytesChangedAfterTheRunLeaveEveryGateNotTested() throws Exception {
        Path mod = candidate();
        Path recordFile = write(record(BridgeForgeShippedHash.of(mod), record -> { }));
        Files.writeString(mod.resolve("data/a.csv"), "id,name\nx,Edited after the test\n");
        var imported = new RunRecordImport().read(recordFile, mod);
        assertThat(imported.results()).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        assertThat(imported.results().getFirst().reason()).contains("other bytes");
        assertThat(imported.profileFile()).isNull();
    }

    @Test void aLegacyRecordWithoutAManifestIsNotEvidence() throws Exception {
        Path mod = candidate();
        var imported = new RunRecordImport().read(write(record(BridgeForgeShippedHash.of(mod), record -> {
            record.remove("manifest_id");
            record.remove("environment");
            ((ObjectNode) record.path("members").get(0)).remove("provenance");
        })), mod);
        assertThat(imported.results()).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        assertThat(imported.results().getFirst().reason()).contains("legacy run record");
    }

    @Test void aMatchingMemberThatIsNotBoundCountsForNothing() throws Exception {
        Path mod = candidate();
        var imported = new RunRecordImport().read(write(record(BridgeForgeShippedHash.of(mod), record ->
                ((ObjectNode) record.path("members").get(0)).put("provenance", "UNKNOWN"))), mod);
        assertThat(imported.results()).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        assertThat(imported.results().getFirst().reason()).contains("not BOUND");
    }

    @Test void aRecordWithoutLaunchEnvironmentCannotProduceAPass() throws Exception {
        Path mod = candidate();
        var imported = new RunRecordImport().read(write(record(BridgeForgeShippedHash.of(mod), record ->
                ((ObjectNode) record.path("environment")).remove("java_version"))), mod);
        assertThat(imported.results()).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        assertThat(imported.results().getFirst().reason()).contains("environment.java_version");
    }

    @Test void aFailingVerdictOrExitCodeFailsTheBootGateAndTheLedgerStatusIsFailed() throws Exception {
        Path mod = candidate();
        String shipped = BridgeForgeShippedHash.of(mod);
        var service = new RunRecordImport();
        var failedVerdict = service.read(write(record(shipped, record ->
                ((ObjectNode) record.path("members").get(0)).put("verdict", "FAIL"))), mod);
        assertThat(gate(failedVerdict, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.FAIL);
        assertThat(gate(failedVerdict, Gate.CAMPAIGN)).isEqualTo(Disposition.NOT_TESTED);
        Path ledger = temporary.resolve("failed/ledger.json");
        service.writeLedger(failedVerdict, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, failedVerdict.candidateSha256()).status())
                .isEqualTo("FAILED");
        var exit = service.read(write(record(shipped, record ->
                ((ObjectNode) record.path("environment")).put("process_exit_code", 1))), mod);
        assertThat(gate(exit, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.FAIL);
        var fatal = service.read(write(record(shipped, record ->
                ((ObjectNode) record.path("triage")).put("FATAL", 1))), mod);
        assertThat(gate(fatal, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.FAIL);
    }

    @Test void aSaveWithoutOrderedReloadEvidenceStaysNotTestedWhileBootAndCampaignPass() throws Exception {
        Path mod = candidate();
        var imported = new RunRecordImport().read(write(record(BridgeForgeShippedHash.of(mod), record ->
                ((ObjectNode) record.path("milestones")).remove("reload_after_save"))), mod);
        assertThat(gate(imported, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.CAMPAIGN)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.SAVE_RELOAD)).isEqualTo(Disposition.NOT_TESTED);
        Path ledger = temporary.resolve("partial/ledger.json");
        new RunRecordImport().writeLedger(imported, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, imported.candidateSha256())).isNotNull();
    }

    @Test void somethingThatIsNotARunRecordIsRefused() throws Exception {
        Path mod = candidate();
        Path other = temporary.resolve("other.json");
        JSON.writeValue(other.toFile(), Map.of("mode", "SOMETHING_ELSE"));
        assertThatThrownBy(() -> new RunRecordImport().read(other, mod)).isInstanceOf(ProjectException.class)
                .hasMessageContaining("LIVE_RUN_RECORD");
    }
}
