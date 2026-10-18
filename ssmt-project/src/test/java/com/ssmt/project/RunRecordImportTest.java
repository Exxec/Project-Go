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

/**
 * Reads the record BridgeForge's own {@code write_run_record} produced for a synthetic tree (R1 format, generated
 * 2026-10-18 with BridgeForge's {@code tests/bound_runs.py} helpers and committed under
 * {@code src/test/resources/bridgeforge-r1}); variations of it are made here. No BridgeForge or game data.
 */
class RunRecordImportTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FIXTURE = Path.of("src/test/resources/bridgeforge-r1");
    private static final Path CANDIDATE = FIXTURE.resolve("candidate");
    @TempDir Path temporary;

    /** The fixture record, adjusted by the caller, written under a fresh name. */
    private Path record(Consumer<ObjectNode> adjust) throws IOException {
        ObjectNode record = (ObjectNode) JSON.readTree(FIXTURE.resolve("R-FIX.json").toFile());
        adjust.accept(record);
        Path file = temporary.resolve("_live/" + record.path("test_id").asText("x") + ".json");
        Files.createDirectories(java.util.Objects.requireNonNull(file.getParent()));
        JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), record);
        return file;
    }

    /** What BridgeForge does not write yet but a Project Go runtime capture needs. */
    private static void withLaunchFacts(ObjectNode record) {
        ((ObjectNode) record.path("manifest")).put("java_executable", "C:/rig/jre/bin/java.exe")
                .put("java_version", "25.0.1");
        record.put("process_exit_code", 0);
    }

    private static Disposition gate(RunRecordImport.Imported imported, Gate gate) {
        return imported.results().stream().filter(item -> item.gate() == gate).findFirst().orElseThrow()
                .disposition();
    }

    private static String reason(RunRecordImport.Imported imported, Gate gate) {
        return imported.results().stream().filter(item -> item.gate() == gate).findFirst().orElseThrow().reason();
    }

    @Test void theJavaPortOfTheShippedHashEqualsTheOneBridgeForgeWroteIntoItsOwnRecord() throws Exception {
        var record = JSON.readTree(FIXTURE.resolve("R-FIX.json").toFile());
        assertThat(BridgeForgeShippedHash.of(CANDIDATE))
                .isEqualTo(record.path("members").get(0).path("shipped_sha256").asText());
    }

    @Test void theRealRecordAsBridgeForgeWritesItTodayLeavesTheGatesPendingAndNamesWhatIsMissing() throws Exception {
        var imported = new RunRecordImport().read(record(record -> { }), CANDIDATE);
        assertThat(imported.results()).hasSize(3).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        String reason = reason(imported, Gate.AUTOMATED_BOOT);
        assertThat(reason).contains("manifest.java_executable", "manifest.java_version", "process_exit_code")
                .doesNotContain("core_baseline_sha256").contains("nothing is guessed");
        assertThat(imported.profileFile()).isNull();
        Path ledger = temporary.resolve("pending/ledger.json");
        new RunRecordImport().writeLedger(imported, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, imported.candidateSha256()).results()).hasSize(13);
    }

    @Test void withTheLaunchFactsPresentBootAndCampaignPassFromTheMilestonesAndSaveReloadStaysOpen() throws Exception {
        var service = new RunRecordImport();
        var imported = service.read(record(RunRecordImportTest::withLaunchFacts), CANDIDATE);
        assertThat(gate(imported, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.CAMPAIGN)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.SAVE_RELOAD)).isEqualTo(Disposition.NOT_TESTED);
        assertThat(reason(imported, Gate.SAVE_RELOAD)).contains("save (true)").contains("not that the save was reloaded");
        Path ledger = temporary.resolve("ledger/ledger.json");
        service.writeLedger(imported, ledger);
        var profile = JSON.readTree(Files.readString(ledger.resolveSibling("runtime-R-FIX.json")));
        assertThat(profile.path("starsectorBuild").asText()).startsWith("core-baseline-sha256:");
        assertThat(profile.path("enabledMods")).extracting(mod -> mod.path("id").asText())
                .containsExactly("lib", "mod_b");
        var summary = new AssuranceLedgerReader().read(ledger, imported.candidateSha256());
        assertThat(summary.results()).filteredOn(item -> item.gate() == Gate.AUTOMATED_BOOT).singleElement()
                .satisfies(item -> assertThat(item.disposition()).isEqualTo(Disposition.PASS));
        service.writeLedger(imported, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, imported.candidateSha256()).results()).hasSize(13);
    }

    @Test void aCampaignWithoutALoadMilestoneOnlyPassesTheBoot() throws Exception {
        var imported = new RunRecordImport().read(record(record -> {
            withLaunchFacts(record);
            ((ObjectNode) record.path("evidence").path("milestones")).put("campaign_load", false);
        }), CANDIDATE);
        assertThat(gate(imported, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.PASS);
        assertThat(gate(imported, Gate.CAMPAIGN)).isEqualTo(Disposition.NOT_TESTED);
    }

    @Test void failureSignsFailTheBootGateAndTheLedgerStatusIsFailed() throws Exception {
        var service = new RunRecordImport();
        for (Consumer<ObjectNode> sign : java.util.List.<Consumer<ObjectNode>>of(
                record -> ((ObjectNode) record.path("members").get(0)).put("verdict", "FAIL"),
                record -> ((ObjectNode) record.path("triage")).put("FATAL", 1),
                record -> ((ObjectNode) record.path("triage")).put("MOD-ERROR", 2),
                record -> record.putArray("jvm_crashes").addObject().put("file", "hs_err.log"),
                record -> record.put("process_exit_code", 1))) {
            var imported = service.read(record(record -> {
                withLaunchFacts(record);
                sign.accept(record);
            }), CANDIDATE);
            assertThat(gate(imported, Gate.AUTOMATED_BOOT)).isEqualTo(Disposition.FAIL);
            assertThat(gate(imported, Gate.CAMPAIGN)).isEqualTo(Disposition.NOT_TESTED);
        }
        var imported = service.read(record(record -> {
            withLaunchFacts(record);
            ((ObjectNode) record.path("triage")).put("FATAL", 1);
        }), CANDIDATE);
        Path ledger = temporary.resolve("failed/ledger.json");
        service.writeLedger(imported, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, imported.candidateSha256()).status()).isEqualTo("FAILED");
    }

    @Test void evidenceBridgeForgeRefusedOrBoundToAnotherManifestIsNotEvidence() throws Exception {
        var refused = new RunRecordImport().read(record(record -> {
            withLaunchFacts(record);
            ((ObjectNode) record.path("evidence")).put("status", "REFUSED").putArray("problems")
                    .add("expected milestone save not observed in the log");
        }), CANDIDATE);
        assertThat(refused.results()).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        assertThat(reason(refused, Gate.AUTOMATED_BOOT)).contains("REFUSED").contains("milestone save");
        var other = new RunRecordImport().read(record(record -> {
            withLaunchFacts(record);
            ((ObjectNode) record.path("evidence")).put("manifest_id", "0".repeat(64));
        }), CANDIDATE);
        assertThat(reason(other, Gate.AUTOMATED_BOOT)).contains("another manifest");
    }

    @Test void legacyUnboundAndOtherBytesRecordsLeaveEveryGateNotTested() throws Exception {
        var legacy = new RunRecordImport().read(record(record -> {
            record.remove("manifest_id");
            record.remove("manifest");
            record.remove("evidence");
            ((ObjectNode) record.path("members").get(0)).remove("provenance");
        }), CANDIDATE);
        assertThat(reason(legacy, Gate.CAMPAIGN)).contains("legacy run record");
        var unbound = new RunRecordImport().read(record(record -> {
            withLaunchFacts(record);
            ((ObjectNode) record.path("members").get(0)).put("provenance", "UNKNOWN");
        }), CANDIDATE);
        assertThat(reason(unbound, Gate.AUTOMATED_BOOT)).contains("not BOUND");
        var otherBytes = new RunRecordImport().read(record(record -> {
            withLaunchFacts(record);
            ((ObjectNode) record.path("members").get(0)).put("shipped_sha256", "0".repeat(64));
        }), CANDIDATE);
        assertThat(reason(otherBytes, Gate.AUTOMATED_BOOT)).contains("other bytes");
        for (var imported : java.util.List.of(legacy, unbound, otherBytes)) {
            assertThat(imported.results()).allMatch(item -> item.disposition() == Disposition.NOT_TESTED);
        }
    }

    @Test void aTreeEditedAfterTheRunNoLongerMatchesTheRecord() throws Exception {
        Path edited = temporary.resolve("edited");
        try (var walk = Files.walk(CANDIDATE)) {
            for (Path path : walk.toList()) {
                Path target = edited.resolve(CANDIDATE.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
        Files.writeString(edited.resolve("data/hulls/ship_data.csv"), "id,hints\nedited,\n");
        var imported = new RunRecordImport().read(record(RunRecordImportTest::withLaunchFacts), edited);
        assertThat(reason(imported, Gate.AUTOMATED_BOOT)).contains("other bytes");
    }

    @Test void somethingThatIsNotARunRecordIsRefused() throws Exception {
        Path other = temporary.resolve("other.json");
        JSON.writeValue(other.toFile(), Map.of("mode", "SOMETHING_ELSE"));
        assertThatThrownBy(() -> new RunRecordImport().read(other, CANDIDATE)).isInstanceOf(ProjectException.class)
                .hasMessageContaining("LIVE_RUN_RECORD");
    }
}
