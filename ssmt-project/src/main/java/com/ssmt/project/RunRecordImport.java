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
 * Reads a BridgeForge live run record ({@code <queue>/_live/<TESTID>.json}, written by
 * {@code live_trust.write_run_record}) as runtime evidence for AUTOMATED_BOOT, CAMPAIGN and SAVE_RELOAD (owner decision
 * 2026-10-14: only when the record's mod hash matches the candidate).
 *
 * <p>The record shape read here is BridgeForge's R1 format (ROADMAP item 75): {@code manifest_id}, {@code manifest}
 * (the pre-launch manifest summary: kind, dependencies with versions, {@code java_executable}), {@code members[]}
 * with {@code shipped_sha256}, {@code verdict} and {@code provenance} BOUND or UNKNOWN, {@code core_baseline_sha256},
 * {@code evidence{status, run_id, manifest_id, log, milestones{main_menu, campaign_load, save, content_ids, combat}}},
 * {@code triage} counts and {@code jvm_crashes}.</p>
 *
 * <p>Evidence rules: the record must carry a manifest id; only a member with provenance {@code BOUND} whose
 * {@code shipped_sha256} equals the candidate's BridgeForge shipped hash ({@link BridgeForgeShippedHash}) counts;
 * {@code evidence.status} must be ACCEPTABLE and name the same manifest. Legacy records, unbound members, mismatching
 * bytes and refused evidence leave the three gates NOT_TESTED with the reason.</p>
 *
 * <p>Project Go's runtime capture also needs a Starsector build identity (taken from {@code core_baseline_sha256}), the
 * Java executable ({@code manifest.java_executable}), a Java version and the process exit code. BridgeForge's record has
     * optional Java version and process exit fields: {@code manifest.java_version} and a top-level
     * {@code process_exit_code}. The producer can now record them, but interactive callers without an observed exit
     * leave it null. Nothing is guessed: if either launch fact is missing, all three gates stay
 * NOT_TESTED and the reason names what is missing. SAVE_RELOAD additionally stays NOT_TESTED because the milestones
 * record that a save finished and a campaign loaded, not that the save was reloaded. The record is copied verbatim
 * beside the ledger as the hashed log of the schema-2 capture.</p>
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
            byte[] profileBytes, String recordFile, byte[] recordBytes, List<String> notes) {
        public Imported {
            results = List.copyOf(results);
            notes = List.copyOf(notes);
            profileBytes = profileBytes == null ? null : profileBytes.clone();
            recordBytes = recordBytes == null ? null : recordBytes.clone();
        }

        @Override public byte[] profileBytes() {
            return profileBytes == null ? null : profileBytes.clone();
        }

        @Override public byte[] recordBytes() {
            return recordBytes == null ? null : recordBytes.clone();
        }
    }

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
        if (root.path("manifest_id").asText("").isBlank() || !root.path("manifest").isObject()) {
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
        JsonNode evidence = root.path("evidence");
        if (!"ACCEPTABLE".equals(evidence.path("status").asText())) {
            JsonNode problems = evidence.path("problems");
            return "BridgeForge's evidence status is " + evidence.path("status").asText("missing") + " (not ACCEPTABLE)"
                    + (problems.isArray() && !problems.isEmpty() ? ": " + problems.get(0).asText() : "");
        }
        if (!root.path("manifest_id").asText().equals(evidence.path("manifest_id").asText())) {
            return "the evidence names another manifest than the record";
        }
        List<String> missing = new ArrayList<>();
        if (root.path("core_baseline_sha256").asText("").isBlank()) {
            missing.add("core_baseline_sha256 (the Starsector build identity)");
        }
        JsonNode manifest = root.path("manifest");
        if (manifest.path("java_executable").asText("").isBlank()) {
            missing.add("manifest.java_executable");
        }
        if (manifest.path("java_version").asText("").isBlank()) {
            missing.add("manifest.java_version");
        }
        if (!root.path("process_exit_code").isInt()) {
            missing.add("process_exit_code");
        }
        for (JsonNode member : root.path("members")) {
            if (member.path("version").asText("").isBlank()) {
                missing.add("members[" + member.path("mod_id").asText() + "].version");
            }
        }
        if (!missing.isEmpty()) {
            return "BridgeForge's run record does not carry " + String.join(", ", missing)
                    + ", which a Project Go runtime capture requires; nothing is guessed";
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
        JsonNode milestones = root.path("evidence").path("milestones");
        boolean clean = triage.path("FATAL").asInt(0) == 0 && triage.path("MOD-ERROR").asInt(0) == 0
                && root.path("jvm_crashes").isEmpty();
        boolean failed = !passing || !clean || root.path("process_exit_code").asInt() != 0;
        boolean menu = milestones.path("main_menu").asBoolean(false);
        boolean campaign = milestones.path("campaign_load").asBoolean(false);
        boolean saved = milestones.path("save").asBoolean(false);

        var boot = failed ? AssuranceSummary.Disposition.FAIL
                : menu ? AssuranceSummary.Disposition.PASS : AssuranceSummary.Disposition.NOT_TESTED;
        var campaignGate = boot == AssuranceSummary.Disposition.PASS && campaign
                ? AssuranceSummary.Disposition.PASS : AssuranceSummary.Disposition.NOT_TESTED;
        var saveGate = AssuranceSummary.Disposition.NOT_TESTED;
        String bootReason = failed ? "the record shows a failure (FAIL verdict, FATAL, MOD-ERROR, JVM crash or non-zero process exit)"
                : menu ? "BridgeForge run " + testId + " reached the main menu on these exact bytes"
                : "the record shows no main menu";
        String campaignReason = campaignGate == AssuranceSummary.Disposition.PASS
                ? "a campaign loaded in BridgeForge run " + testId
                : "no campaign load in the record (or the boot did not pass)";
        String saveReason = "BridgeForge's milestones record a save (" + saved + ") and a campaign load (" + campaign
                + ") but not that the save was reloaded; no reload evidence exists in the record";

        String recordFile = "run-record-" + testId + ".json";
        String profileFile = PROFILE_PREFIX + testId + ".json";
        String scenarioPrefix = "bridgeforge-run:" + testId + ":";
        List<RuntimeEvidenceReader.ScenarioResult> scenarios = List.of(
                scenario(AssuranceSummary.Gate.AUTOMATED_BOOT, boot, scenarioPrefix, bootReason),
                scenario(AssuranceSummary.Gate.CAMPAIGN, campaignGate, scenarioPrefix, campaignReason),
                scenario(AssuranceSummary.Gate.SAVE_RELOAD, saveGate, scenarioPrefix, saveReason));
        List<RuntimeEvidenceReader.EnabledMod> enabled = new ArrayList<>();
        // The summary keeps the staged providers (dependencies) in enabled order, then the run's own members; the
        // manifest summary does not keep the enabled list itself, so this order is BridgeForge's staging order.
        root.path("manifest").path("dependencies").fields().forEachRemaining(dependency ->
                enabled.add(new RuntimeEvidenceReader.EnabledMod(dependency.getKey(),
                        dependency.getValue().path("version").asText())));
        for (JsonNode each : root.path("members")) {
            enabled.add(new RuntimeEvidenceReader.EnabledMod(each.path("mod_id").asText(),
                    each.path("version").asText()));
        }
        List<String> modals = new ArrayList<>();
        var profile = new RuntimeEvidenceReader.Profile(2, fingerprint,
                "core-baseline-sha256:" + root.path("core_baseline_sha256").asText(),
                enabled, enabled.stream().map(RuntimeEvidenceReader.EnabledMod::id).toList(),
                root.path("manifest").path("java_executable").asText(), root.path("manifest").path("java_version").asText(),
                root.path("process_exit_code").asInt(),
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
