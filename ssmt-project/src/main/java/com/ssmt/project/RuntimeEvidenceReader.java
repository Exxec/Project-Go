package com.ssmt.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Validates captured launch context, but never treats it as gameplay proof. */
public final class RuntimeEvidenceReader {
    private static final int MAX_RECORD_BYTES = 1024 * 1024;
    private static final long MAX_LOG_BYTES = 64L * 1024 * 1024;

    public record EnabledMod(String id, String version) {
        public EnabledMod {
            requireText(id, "Enabled mod ID");
            requireText(version, "Enabled mod version");
        }
    }

    public record LogReference(String path, String sha256) {
        public LogReference {
            if (path == null || path.isBlank() || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid runtime log reference");
            }
        }
    }

    /** One explicitly named runtime-gate outcome observed in this capture. */
    public record ScenarioResult(AssuranceSummary.Gate gate,
            AssuranceSummary.Disposition disposition, String scenario, String reason) {
        public ScenarioResult {
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(disposition, "disposition");
            requireText(scenario, "Runtime scenario");
            reason = Objects.requireNonNullElse(reason, "");
            if (!runtimeGate(gate)) {
                throw new IllegalArgumentException("Runtime capture cannot claim gate: " + gate);
            }
            if (disposition != AssuranceSummary.Disposition.PASS
                    && disposition != AssuranceSummary.Disposition.FAIL
                    && disposition != AssuranceSummary.Disposition.NOT_TESTED) {
                throw new IllegalArgumentException("Invalid runtime scenario disposition: " + disposition);
            }
            if (disposition != AssuranceSummary.Disposition.PASS && reason.isBlank()) {
                throw new IllegalArgumentException("Non-passing runtime scenario requires a reason");
            }
        }
    }

    /** Exact observed launch context, bound to the candidate bytes. */
    public record Profile(int schemaVersion, String candidateSha256, String starsectorBuild,
            List<EnabledMod> enabledMods, List<String> loadOrder, String javaExecutable,
            String javaVersion, Integer processExitCode, List<LogReference> logs,
            List<String> modalDialogs, List<ScenarioResult> scenarios) {
        /** Preserves the schema-1 construction API for launch-context-only captures. */
        public Profile(int schemaVersion, String candidateSha256, String starsectorBuild,
                List<EnabledMod> enabledMods, List<String> loadOrder, String javaExecutable,
                String javaVersion, Integer processExitCode, List<LogReference> logs,
                List<String> modalDialogs) {
            this(schemaVersion, candidateSha256, starsectorBuild, enabledMods, loadOrder,
                    javaExecutable, javaVersion, processExitCode, logs, modalDialogs, List.of());
        }

        public Profile {
            requireHash(candidateSha256);
            requireText(starsectorBuild, "Starsector build");
            enabledMods = List.copyOf(Objects.requireNonNull(enabledMods, "enabledMods"));
            loadOrder = List.copyOf(Objects.requireNonNull(loadOrder, "loadOrder"));
            requireText(javaExecutable, "Java executable");
            requireText(javaVersion, "Java version");
            Objects.requireNonNull(processExitCode, "processExitCode");
            logs = List.copyOf(Objects.requireNonNull(logs, "logs"));
            modalDialogs = List.copyOf(Objects.requireNonNull(modalDialogs, "modalDialogs"));
            scenarios = List.copyOf(Objects.requireNonNullElse(scenarios, List.of()));
            if (schemaVersion != 1 && schemaVersion != 2) {
                throw new IllegalArgumentException("Unsupported runtime evidence schema");
            }
            if (schemaVersion == 1 && !scenarios.isEmpty()) {
                throw new IllegalArgumentException("Runtime scenarios require schema 2");
            }
            var modIds = new HashSet<String>();
            for (EnabledMod mod : enabledMods) {
                if (!modIds.add(mod.id())) { throw new IllegalArgumentException("Repeated enabled mod: " + mod.id()); }
            }
            var orderedIds = new HashSet<String>();
            for (String id : loadOrder) {
                requireText(id, "Load-order mod ID");
                if (!orderedIds.add(id)) { throw new IllegalArgumentException("Repeated load-order mod: " + id); }
            }
            if (!modIds.equals(orderedIds)) {
                throw new IllegalArgumentException("Load order must contain every enabled mod exactly once");
            }
            var scenarioGates = new HashSet<AssuranceSummary.Gate>();
            for (ScenarioResult scenario : scenarios) {
                if (!scenarioGates.add(scenario.gate())) {
                    throw new IllegalArgumentException("Repeated runtime scenario gate: " + scenario.gate());
                }
            }
        }
    }

    /** Requires a schema-2 scenario record that exactly matches one terminal ledger result. */
    public void verifyScenario(Profile profile, AssuranceSummary.Result result) throws IOException {
        if (!runtimeGate(result.gate())
                || (result.disposition() != AssuranceSummary.Disposition.PASS
                && result.disposition() != AssuranceSummary.Disposition.FAIL)) {
            throw new IOException("Runtime scenario verification requires a terminal runtime gate");
        }
        if (profile.schemaVersion() != 2) {
            throw new IOException("Runtime PASS/FAIL requires schema 2 scenario evidence");
        }
        ScenarioResult scenario = profile.scenarios().stream()
                .filter(candidate -> candidate.gate() == result.gate())
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Runtime evidence does not contain gate: " + result.gate()));
        if (scenario.disposition() != result.disposition()
                || !scenario.scenario().equals(result.scenario())) {
            throw new IOException("Runtime evidence does not match ledger scenario: " + result.gate());
        }
        if (profile.logs().isEmpty()) {
            throw new IOException("Runtime PASS/FAIL requires at least one captured log");
        }
        if (result.disposition() == AssuranceSummary.Disposition.PASS
                && profile.processExitCode() != 0) {
            throw new IOException("Passing runtime evidence requires process exit code 0");
        }
    }

    /** Reads an exact-candidate profile and hashes all bounded, contained log references. */
    public Profile read(Path profileFile, String expectedCandidateSha256) throws IOException {
        Path file = profileFile.toAbsolutePath().normalize();
        safeFile(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_RECORD_BYTES + 1); }
        if (bytes.length > MAX_RECORD_BYTES) { throw new IOException("Runtime evidence exceeds 1 MiB"); }
        Profile profile;
        try {
            profile = JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readValue(bytes, Profile.class);
        } catch (IllegalArgumentException exception) { throw new IOException("Invalid runtime evidence", exception); }
        if (profile == null || !profile.candidateSha256().equals(expectedCandidateSha256)) {
            throw new IOException("Runtime evidence belongs to a different candidate");
        }
        Path root = Objects.requireNonNull(file.getParent());
        var used = new HashSet<String>();
        long total = 0;
        for (LogReference reference : profile.logs()) {
            if (!used.add(reference.path())) { throw new IOException("Repeated runtime log reference"); }
            Path relative = Path.of(reference.path());
            Path log = root.resolve(relative).normalize();
            if (reference.path().contains("\\") || reference.path().contains(":")
                    || relative.isAbsolute() || !log.startsWith(root)) {
                throw new IOException("Runtime log requires a portable contained relative path");
            }
            safeFile(log);
            var digest = digest();
            try (var input = Files.newInputStream(log)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_LOG_BYTES) { throw new IOException("Runtime log byte budget exceeded"); }
                    digest.update(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(reference.sha256())) {
                throw new IOException("Runtime log hash mismatch: " + reference.path());
            }
        }
        return profile;
    }

    private static void requireText(String text, String name) {
        if (text == null || text.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
    }
    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Candidate SHA-256 must be 64 lowercase hex characters");
        }
    }
    private static boolean runtimeGate(AssuranceSummary.Gate gate) {
        return switch (gate) {
            case AUTOMATED_BOOT, CAMPAIGN, COMBAT, SAVE_RELOAD, UPGRADE_COMPATIBILITY -> true;
            default -> false;
        };
    }
    private static void safeFile(Path file) throws IOException {
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) { throw new IOException("Linked runtime evidence paths require review"); }
        }
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Runtime evidence must use regular files");
        }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
