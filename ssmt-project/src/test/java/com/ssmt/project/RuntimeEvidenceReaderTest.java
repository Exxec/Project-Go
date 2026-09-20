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

    @Test void readsHistoricalSchemaOneJsonWithoutScenariosProperty() throws Exception {
        Path log = Files.writeString(root.resolve("historical.log"), "historical launch");
        var mapper = new ObjectMapper();
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(
                profile(List.of(new RuntimeEvidenceReader.LogReference(
                        "historical.log", sha256(log)))));
        json.remove("scenarios");
        Path file = root.resolve("historical-runtime.json");
        mapper.writeValue(file.toFile(), json);

        assertThat(new RuntimeEvidenceReader().read(file, HASH).scenarios()).isEmpty();
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
                "java", "25", 0, List.of(), List.of(), List.of()))
                .hasMessageContaining("Load order");
    }

    @Test void terminalLedgerScenarioRequiresMatchingSchemaTwoCapture() throws Exception {
        Path log = Files.writeString(root.resolve("scenario.log"), "campaign completed");
        var result = new AssuranceSummary.Result(AssuranceSummary.Gate.CAMPAIGN,
                AssuranceSummary.Disposition.PASS, HASH, "new-campaign",
                "runtime.json", "");
        var reader = new RuntimeEvidenceReader();
        var profile = new RuntimeEvidenceReader.Profile(2, HASH, "0.98a-RC8",
                List.of(new RuntimeEvidenceReader.EnabledMod("nightcross", "1.0")),
                List.of("nightcross"), "java.exe", "25", 0,
                List.of(new RuntimeEvidenceReader.LogReference("scenario.log", sha256(log))),
                List.of(), List.of(new RuntimeEvidenceReader.ScenarioResult(
                        AssuranceSummary.Gate.CAMPAIGN, AssuranceSummary.Disposition.PASS,
                        "new-campaign", "")));

        reader.verifyScenario(profile, result);
        assertThatThrownBy(() -> reader.verifyScenario(profile,
                new AssuranceSummary.Result(AssuranceSummary.Gate.COMBAT,
                        AssuranceSummary.Disposition.PASS, HASH, "combat",
                        "runtime.json", "")))
                .hasMessageContaining("does not contain gate");
    }

    private static RuntimeEvidenceReader.Profile profile(List<RuntimeEvidenceReader.LogReference> logs) {
        return new RuntimeEvidenceReader.Profile(1, HASH, "0.98a-RC8",
                List.of(new RuntimeEvidenceReader.EnabledMod("nightcross", "1.0")), List.of("nightcross"),
                "java.exe", "25", 0, logs, List.of(), List.of());
    }
    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
