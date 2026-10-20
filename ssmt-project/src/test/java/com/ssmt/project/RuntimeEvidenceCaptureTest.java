package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic observations validate storage and refusal behaviour, never live-game acceptance. */
class RuntimeEvidenceCaptureTest {
    @TempDir Path temporary;

    private Path candidate() throws Exception {
        Path candidate = temporary.resolve("candidate");
        Files.createDirectories(candidate);
        Files.writeString(candidate.resolve("mod_info.json"), "{\"id\":\"capture\",\"version\":\"1\"}");
        return candidate;
    }

    private RuntimeEvidenceReader.Profile profile(Path candidate, Path logs) throws Exception {
        Files.createDirectories(logs);
        Path log = logs.resolve("session.log");
        Files.writeString(log, "synthetic observation\n");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(log)));
        var files = new CandidateInventory().capture(candidate);
        String fingerprint = InventoryFingerprint.tree(files.stream().map(file ->
                new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
        return new RuntimeEvidenceReader.Profile(2, fingerprint, "synthetic-core", List.of(), List.of(),
                "synthetic-java", "17.0.1", 0, List.of(new RuntimeEvidenceReader.LogReference("session.log", hash)),
                List.of(), List.of(new RuntimeEvidenceReader.ScenarioResult(AssuranceSummary.Gate.AUTOMATED_BOOT,
                        AssuranceSummary.Disposition.PASS, "synthetic-boot", "")));
    }

    @Test void capturesTheReaderSchemaAndVerifiedLogsWithoutChangingCandidateOrOriginalLog() throws Exception {
        Path candidate = candidate();
        Path logs = temporary.resolve("observed-logs");
        var profile = profile(candidate, logs);
        byte[] before = Files.readAllBytes(candidate.resolve("mod_info.json"));
        byte[] originalLog = Files.readAllBytes(logs.resolve("session.log"));
        Path destination = temporary.resolve("evidence");
        Path saved = RuntimeEvidenceCapture.INSTANCE.capture(candidate, destination, profile, logs);
        assertThat(new RuntimeEvidenceReader().read(saved, profile.candidateSha256())).isEqualTo(profile);
        assertThat(Files.readAllBytes(candidate.resolve("mod_info.json"))).isEqualTo(before);
        assertThat(Files.readAllBytes(logs.resolve("session.log"))).isEqualTo(originalLog);
        assertThatThrownBy(() -> RuntimeEvidenceCapture.INSTANCE.capture(candidate, destination, profile, logs))
                .isInstanceOf(ProjectException.class).hasMessageContaining("must be new");
    }

    @Test void refusesChangedCandidateOrLogBeforePublishing() throws Exception {
        Path candidate = candidate();
        Path logs = temporary.resolve("observed-logs");
        var profile = profile(candidate, logs);
        Path destination = temporary.resolve("evidence");
        Files.writeString(logs.resolve("session.log"), "changed");
        assertThatThrownBy(() -> RuntimeEvidenceCapture.INSTANCE.capture(candidate, destination, profile, logs))
                .isInstanceOf(ProjectException.class).hasMessageContaining("hash mismatch");
        assertThat(destination).doesNotExist();
        Files.writeString(candidate.resolve("extra.txt"), "changed");
        assertThatThrownBy(() -> RuntimeEvidenceCapture.INSTANCE.capture(candidate, destination, profile, logs))
                .isInstanceOf(ProjectException.class).hasMessageContaining("different candidate bytes");
        assertThat(destination).doesNotExist();
    }

    @Test void refusesCandidateContainedDestination() throws Exception {
        Path candidate = candidate();
        Path logs = temporary.resolve("observed-logs");
        var profile = profile(candidate, logs);
        assertThatThrownBy(() -> RuntimeEvidenceCapture.INSTANCE.capture(candidate, candidate.resolve("evidence"), profile, logs))
                .isInstanceOf(ProjectException.class).hasMessageContaining("outside the candidate");
        assertThat(candidate.resolve("evidence")).doesNotExist();
    }

    @Test void aNonzeroExitCannotBeStoredAsPassingScenarioEvidence() throws Exception {
        Path candidate = candidate();
        Path logs = temporary.resolve("observed-logs");
        var profile = profile(candidate, logs);
        var invalid = new RuntimeEvidenceReader.Profile(profile.schemaVersion(), profile.candidateSha256(),
                profile.starsectorBuild(), profile.enabledMods(), profile.loadOrder(), profile.javaExecutable(),
                profile.javaVersion(), 7, profile.logs(), profile.modalDialogs(), profile.scenarios());
        Path destination = temporary.resolve("evidence");
        assertThatThrownBy(() -> RuntimeEvidenceCapture.INSTANCE.capture(candidate, destination, invalid, logs))
                .isInstanceOf(ProjectException.class).hasMessageContaining("exit code 0");
        assertThat(destination).doesNotExist();
    }
}
