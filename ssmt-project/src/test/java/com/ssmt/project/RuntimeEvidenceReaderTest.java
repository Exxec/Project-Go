package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeEvidenceReaderTest {
    private static final String HASH = "a".repeat(64);
    @TempDir Path root;

    @Test void readsCandidateBoundLaunchProfileAndHashesLogs() throws Exception {
        Path log = Files.writeString(root.resolve("starsector.log"), "launcher reached menu");
        Path file = root.resolve("runtime.json");
        new ObjectMapper().writeValue(file.toFile(), profile(List.of(new RuntimeEvidenceReader.LogReference(
                "starsector.log", sha256(log)))));
        assertThat(new RuntimeEvidenceReader().read(file, HASH).starsectorBuild()).isEqualTo("0.98a-RC8");
    }

    @Test void rejectsMismatchedLogAndIncompleteLoadOrder() throws Exception {
        Files.writeString(root.resolve("starsector.log"), "original");
        Path file = root.resolve("runtime.json");
        new ObjectMapper().writeValue(file.toFile(), profile(List.of(new RuntimeEvidenceReader.LogReference(
                "starsector.log", "b".repeat(64)))));
        assertThatThrownBy(() -> new RuntimeEvidenceReader().read(file, HASH))
                .hasMessageContaining("Runtime log hash mismatch");
        assertThatThrownBy(() -> new RuntimeEvidenceReader.Profile(1, HASH, "0.98a-RC8",
                List.of(new RuntimeEvidenceReader.EnabledMod("nightcross", "1.0")), List.of(),
                "java", "25", 0, List.of(), List.of())).hasMessageContaining("Load order");
    }

    private static RuntimeEvidenceReader.Profile profile(List<RuntimeEvidenceReader.LogReference> logs) {
        return new RuntimeEvidenceReader.Profile(1, HASH, "0.98a-RC8",
                List.of(new RuntimeEvidenceReader.EnabledMod("nightcross", "1.0")), List.of("nightcross"),
                "java.exe", "25", 0, logs, List.of());
    }
    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
