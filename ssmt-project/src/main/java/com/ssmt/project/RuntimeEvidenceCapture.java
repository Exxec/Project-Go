package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/** Persists explicitly supplied observations; never launches a game or manufactures launch context. */
public final class RuntimeEvidenceCapture {
    public static final RuntimeEvidenceCapture INSTANCE = new RuntimeEvidenceCapture();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROFILE = "runtime-profile.json";
    private static final int MAX_LOG_BYTES = 64 * 1024 * 1024;

    private RuntimeEvidenceCapture() { }

    /**
     * Publishes a new evidence directory from an observed, candidate-bound profile and its hashed logs.
     * The caller supplies actual observations; unknown launch context must remain NOT_TESTED upstream.
     * Nothing is written into the candidate or observed-log tree, and an existing destination is refused.
     */
    public Path capture(Path candidate, Path destination, RuntimeEvidenceReader.Profile observed, Path logRoot)
            throws ProjectException {
        Path staging = null;
        try {
            Path source = candidate.toRealPath();
            Path logs = logRoot.toRealPath();
            Path requested = destination.toAbsolutePath().normalize();
            Path parent = java.util.Objects.requireNonNull(requested.getParent()).toRealPath();
            Path target = parent.resolve(requested.getFileName());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || target.startsWith(source) || source.startsWith(target)
                    || target.startsWith(logs) || logs.startsWith(target)) {
                throw new IOException("Evidence destination must be new and outside the candidate and observed logs");
            }
            String fingerprint = fingerprint(source);
            if (!fingerprint.equals(observed.candidateSha256())) {
                throw new IOException("Observed profile belongs to different candidate bytes");
            }
            byte[] profile = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(observed);
            if (profile.length > 1024 * 1024) {
                throw new IOException("Runtime profile exceeds 1 MiB");
            }
            staging = Files.createTempDirectory(parent, ".ssmt-runtime-");
            int remaining = MAX_LOG_BYTES;
            for (var reference : observed.logs()) {
                Path relative = BridgeForgeTranslationDocument.safePath(reference.path());
                Path input = logs.resolve(relative).normalize();
                if (!input.startsWith(logs) || !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)
                        || !input.toRealPath().equals(input)) {
                    throw new IOException("Observed log must be a contained regular file: " + reference.path());
                }
                byte[] bytes;
                try (var stream = Files.newInputStream(input)) {
                    bytes = stream.readNBytes(remaining + 1);
                }
                if (bytes.length > remaining) {
                    throw new IOException("Runtime log byte budget exceeded");
                }
                remaining -= bytes.length;
                Path output = staging.resolve(relative);
                Files.createDirectories(java.util.Objects.requireNonNull(output.getParent()));
                Files.write(output, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            Path stagedProfile = staging.resolve(PROFILE);
            Files.write(stagedProfile, profile, java.nio.file.StandardOpenOption.CREATE_NEW);
            var reader = new RuntimeEvidenceReader();
            var verified = reader.read(stagedProfile, fingerprint);
            for (var scenario : verified.scenarios()) {
                if (scenario.disposition() != AssuranceSummary.Disposition.NOT_TESTED) {
                    reader.verifyScenario(verified, new AssuranceSummary.Result(scenario.gate(), scenario.disposition(),
                            fingerprint, scenario.scenario(), PROFILE, scenario.reason()));
                }
            }
            if (!fingerprint.equals(fingerprint(source))) {
                throw new IOException("Candidate changed during evidence capture");
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            staging = null;
            return target.resolve(PROFILE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not capture runtime evidence: " + exception.getMessage(), exception);
        } finally {
            if (staging != null) {
                try (var walk = Files.walk(staging)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException cleanup) {
                    System.getLogger(RuntimeEvidenceCapture.class.getName()).log(System.Logger.Level.WARNING,
                            "Could not remove unpublished runtime evidence staging", cleanup);
                }
            }
        }
    }

    private static String fingerprint(Path candidate) throws IOException {
        var files = new CandidateInventory().capture(candidate);
        return InventoryFingerprint.tree(files.stream().map(file ->
                new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
    }
}
