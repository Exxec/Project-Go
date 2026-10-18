package com.ssmt.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a BridgeForge live run record ({@code <queue>/_live/<TESTID>.json}) as runtime evidence for AUTOMATED_BOOT,
 * CAMPAIGN and SAVE_RELOAD (owner decision 2026-10-14: only when the record's mod hash matches the candidate).
 *
 * <p>Evidence rules: the record must be bound to a launch manifest ({@code manifest_id}; BridgeForge item 75 R1); only a
 * member with provenance {@code BOUND} whose {@code shipped_sha256} equals the candidate's BridgeForge shipped hash
 * ({@link BridgeForgeShippedHash}) counts; the record must carry the launch environment (Starsector build, Java,
 * process exit code). Legacy records, unbound members and mismatching bytes leave the three gates NOT_TESTED. The
 * record is read only; a schema-2 runtime capture with the record as its hashed log is written for the ledger.</p>
 *
 * <p>Expected record fields beyond today's format (written by R1, which is specified but not yet built in BridgeForge):
 * {@code manifest_id}, {@code members[].provenance}, {@code milestones{main_menu_reached, campaign_loads[],
 * finished_saving_count, reload_after_save}}, {@code environment{starsector_build, java_executable, java_version,
 * process_exit_code, enabled_mods[]}}. Names are reconciled with BridgeForge when R1 lands.</p>
 */
public final class RunRecordImport {
    /** Evidence file names beside the ledger are derived from the test id. */
    public static final String PROFILE_PREFIX = "runtime-";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_RECORD_BYTES = 1024 * 1024;
    private static final Set<AssuranceSummary.Gate> GATES = Set.of(AssuranceSummary.Gate.AUTOMATED_BOOT,
            AssuranceSummary.Gate.CAMPAIGN, AssuranceSummary.Gate.SAVE_RELOAD);

    /**
     * What an import decided.
     *
     * @param profileFile ledger-relative name of the runtime capture, or null when none was written
     * @param recordFile ledger-relative name of the verbatim record copy the capture lists as its log
     */
    public record Imported(String candidateSha256, List<AssuranceSummary.Result> results, String profileFile,
            byte[] profileBytes, String recordFile, byte[] recordBytes, List<String> notes) { }

    /** Reads the record and decides the three gates for the candidate directory. */
    public Imported read(Path record, Path candidate) throws ProjectException {
        try {
            if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS) || Files.size(record) > MAX_RECORD_BYTES) {
                throw new IOException("The run record must be a regular file of at most 1 MiB");
            }
            byte[] recordBytes = Files.readAllBytes(record);
            JsonNode root = JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build()
                    .readTree(recordBytes);
            if (!"LIVE_RUN_RECORD".equals(root.path("mode").asText())) {
                throw new IOException("Not a BridgeForge live run record (mode LIVE_RUN_RECORD expected)");
            }
            String testId = root.path("test_id").asText("");
            if (!testId.matches("[A-Za-z0-9._-]{1,120}")) {
                throw new IOException("The run record has no usable test_id");
            }
            var inventory = new CandidateInventory().capture(candidate);
            String fingerprint = InventoryFingerprint.tree(inventory.stream().map(file ->
                    new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
            String shipped = BridgeForgeShippedHash.of(candidate);
            List<String> notes = new ArrayList<>();
            String refusal = refusal(root, shipped, notes);
            if (refusal != null) {
                return pending(fingerprint, refusal, notes);
            }
            JsonNode member = null;
            boolean passing = true;
            for (JsonNode candidateMember : root.path("members")) {
                if (shipped.equals(candidateMember.path("shipped_sha256").asText())
                        && "BOUND".equals(candidateMember.path("provenance").asText())) {
                    member = candidateMember;
                    passing &= "PASS".equals(candidateMember.path("verdict").asText());
                }
            }
            return decide(root, member, passing, fingerprint, testId, recordBytes, notes);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException exception) {
            throw new ProjectException("Could not import the run record: " + exception.getMessage(), exception);
        }
    }

    private static String refusal(JsonNode root, String shipped, List<String> notes) {
        if (root.path("manifest_id").asText("").isBlank()) {
            return "legacy run record: no launch manifest, provenance UNKNOWN; a record without a manifest is not evidence";
        }
        boolean sameBytes = false;
        boolean bound = false;
        for (JsonNode member : root.path("members")) {
            if (shipped.equals(member.path("shipped_sha256").asText())) {
                sameBytes = true;
                bound |= "BOUND".equals(member.path("provenance").asText());
            }
        }
        if (!sameBytes) {
            notes.add("no member of the record has the candidate's shipped hash " + shipped);
            return "the record tested other bytes than this candidate";
        }
        if (!bound) {
            return "the matching member's provenance is not BOUND";
        }
        JsonNode environment = root.path("environment");
        for (String field : List.of("starsector_build", "java_executable", "java_version")) {
            if (environment.path(field).asText("").isBlank()) {
                return "the record lacks environment." + field;
            }
        }
        if (!environment.path("process_exit_code").isInt()) {
            return "the record lacks environment.process_exit_code";
        }
        return null;
    }

    private static Imported pending(String fingerprint, String reason, List<String> notes) {
        notes.add(reason);
        List<AssuranceSummary.Result> results = GATES.stream().sorted().map(gate -> new AssuranceSummary.Result(gate,
                AssuranceSummary.Disposition.NOT_TESTED, fingerprint, gate.name(), "", reason)).toList();
        return new Imported(fingerprint, results, null, null, null, null, List.copyOf(notes));
    }

    private Imported decide(JsonNode root, JsonNode member, boolean passing, String fingerprint, String testId,
            byte[] recordBytes, List<String> notes) throws IOException, NoSuchAlgorithmException {
        JsonNode triage = root.path("triage");
        JsonNode milestones = root.path("milestones");
        JsonNode environment = root.path("environment");
        boolean clean = triage.path("FATAL").asInt(0) == 0 && triage.path("MOD-ERROR").asInt(0) == 0
                && root.path("jvm_crashes").isEmpty();
        boolean failed = !passing || !clean || environment.path("process_exit_code").asInt() != 0;
        boolean menu = milestones.path("main_menu_reached").asBoolean(false);
        boolean campaign = !milestones.path("campaign_loads").isEmpty();
        boolean saved = milestones.path("finished_saving_count").asInt(0) > 0;
        boolean reload = milestones.path("reload_after_save").asBoolean(false);

        var boot = failed ? AssuranceSummary.Disposition.FAIL
                : menu ? AssuranceSummary.Disposition.PASS : AssuranceSummary.Disposition.NOT_TESTED;
        var campaignGate = boot == AssuranceSummary.Disposition.PASS && campaign
                ? AssuranceSummary.Disposition.PASS : AssuranceSummary.Disposition.NOT_TESTED;
        var saveGate = campaignGate == AssuranceSummary.Disposition.PASS && saved && reload
                ? AssuranceSummary.Disposition.PASS : AssuranceSummary.Disposition.NOT_TESTED;
        String bootReason = failed ? "the record shows a failure (FAIL verdict, FATAL, MOD-ERROR, JVM crash or non-zero process exit)"
                : menu ? "BridgeForge run " + testId + " reached the main menu on these exact bytes"
                : "the record shows no main menu";
        String campaignReason = campaignGate == AssuranceSummary.Disposition.PASS
                ? "a campaign loaded in BridgeForge run " + testId
                : "no campaign load in the record (or the boot did not pass)";
        String saveReason = saveGate == AssuranceSummary.Disposition.PASS
                ? "a save was made and reloaded in BridgeForge run " + testId
                : "no ordered save-then-reload evidence in the record (saves: " + saved + ", reload flag: " + reload + ")";

        String recordFile = "run-record-" + testId + ".json";
        String profileFile = PROFILE_PREFIX + testId + ".json";
        String scenarioPrefix = "bridgeforge-run:" + testId + ":";
        List<RuntimeEvidenceReader.ScenarioResult> scenarios = List.of(
                scenario(AssuranceSummary.Gate.AUTOMATED_BOOT, boot, scenarioPrefix, bootReason),
                scenario(AssuranceSummary.Gate.CAMPAIGN, campaignGate, scenarioPrefix, campaignReason),
                scenario(AssuranceSummary.Gate.SAVE_RELOAD, saveGate, scenarioPrefix, saveReason));
        List<RuntimeEvidenceReader.EnabledMod> enabled = new ArrayList<>();
        JsonNode listed = environment.path("enabled_mods").isArray() ? environment.path("enabled_mods") : null;
        if (listed != null) {
            listed.forEach(mod -> enabled.add(new RuntimeEvidenceReader.EnabledMod(mod.path("id").asText(),
                    mod.path("version").asText())));
        } else {
            for (JsonNode each : root.path("members")) {
                enabled.add(new RuntimeEvidenceReader.EnabledMod(each.path("mod_id").asText(),
                        each.path("version").asText()));
            }
        }
        List<String> modals = new ArrayList<>();
        root.path("modal_dialogs").forEach(dialog -> modals.add(dialog.asText()));
        var profile = new RuntimeEvidenceReader.Profile(2, fingerprint, environment.path("starsector_build").asText(),
                enabled, enabled.stream().map(RuntimeEvidenceReader.EnabledMod::id).toList(),
                environment.path("java_executable").asText(), environment.path("java_version").asText(),
                environment.path("process_exit_code").asInt(),
                List.of(new RuntimeEvidenceReader.LogReference(recordFile, sha256(recordBytes))), modals, scenarios);
        byte[] profileBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(profile);
        List<AssuranceSummary.Result> results = new ArrayList<>();
        for (var item : scenarios) {
            results.add(new AssuranceSummary.Result(item.gate(), item.disposition(), fingerprint, item.scenario(),
                    item.disposition() == AssuranceSummary.Disposition.NOT_TESTED ? "" : profileFile,
                    item.disposition() == AssuranceSummary.Disposition.PASS ? "" : item.reason()));
        }
        notes.add("matched BOUND member " + member.path("workspace").asText() + " by shipped hash");
        return new Imported(fingerprint, List.copyOf(results), profileFile, profileBytes, recordFile, recordBytes,
                List.copyOf(notes));
    }

    private static RuntimeEvidenceReader.ScenarioResult scenario(AssuranceSummary.Gate gate,
            AssuranceSummary.Disposition disposition, String prefix, String reason) {
        return new RuntimeEvidenceReader.ScenarioResult(gate, disposition, prefix + gate.name(), reason);
    }

    /**
     * Writes the evidence files beside the ledger and records the three gates in it (merging into an existing ledger of
     * the same candidate).
     */
    public AssuranceLedgerReader.Ledger writeLedger(Imported imported, Path ledgerFile) throws ProjectException {
        try {
            Path directory = java.util.Objects.requireNonNull(ledgerFile.toAbsolutePath().getParent());
            Files.createDirectories(directory);
            Map<String, String> evidence = new java.util.HashMap<>();
            if (imported.profileFile() != null) {
                evidence.put(imported.profileFile(), sha256(imported.profileBytes()));
            }
            var ledger = AssuranceLedgerWriter.merge(ledgerFile, imported.candidateSha256(), GATES, imported.results(),
                    evidence);
            if (imported.profileFile() != null) {
                TranslationSnapshot.writeAtomic(directory.resolve(imported.recordFile()), imported.recordBytes());
                TranslationSnapshot.writeAtomic(directory.resolve(imported.profileFile()), imported.profileBytes());
            }
            return ledger;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ProjectException("Could not write the ledger: " + exception.getMessage(), exception);
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
