package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cross-repo contract (Project Go P9, BridgeForge 94.G2; GR-1): BridgeForge's gene.json contract, copied byte for byte to
 * {@code fixtures/gene-contract} and pinned by CONTRACT.json's SHA-256 in {@code bridgeforge-pins/gene-contract.sha256},
 * where BridgeForge's {@code gene sisters} check reads it. Every fixture must be judged here as BridgeForge judges it. To
 * take a new contract: copy BridgeForge's {@code docs/contracts/gene} from its main branch with {@code git archive}, write
 * the new pin, and make these tests pass.
 */
class BridgeForgeGeneContractTest {
    private static final Path CONTRACT = Path.of("../fixtures/gene-contract");
    private static final Path PIN = Path.of("../bridgeforge-pins/gene-contract.sha256");
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path directory;

    @Test void thePinnedContractIsTheCopyThatIsHere() throws Exception {
        assertThat(contractProblems(CONTRACT, Files.readString(PIN, StandardCharsets.US_ASCII).strip())).isEmpty();
    }

    @Test void aChangedContractFileIsCaught() throws Exception {
        Path copy = directory.resolve("gene-contract");
        copyTree(CONTRACT, copy);
        Path fixture = copy.resolve("fixtures/valid-hullmod.json");
        Files.write(fixture, (Files.readString(fixture, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8));
        String pin = Files.readString(PIN, StandardCharsets.US_ASCII).strip();
        assertThat(contractProblems(copy, pin)).containsExactly("fixtures/valid-hullmod.json differs from CONTRACT.json");
        assertThat(contractProblems(CONTRACT, "0".repeat(64))).containsExactly("CONTRACT.json differs from the pin");
    }

    @Test void theReaderAcceptsTheContractsSchemaKindsAndVersions() throws Exception {
        JsonNode contract = read(CONTRACT.resolve("CONTRACT.json"));
        JsonNode schema = read(CONTRACT.resolve("gene.schema.json")).get("properties");
        assertThat(contract.get("schema_version").asInt()).isEqualTo(BridgeForgeGeneManifest.SCHEMA_VERSION);
        assertThat(texts(schema.get("kind").get("enum"))).isEqualTo(BridgeForgeGeneManifest.KINDS);
        List<Integer> withdrawn = new ArrayList<>();
        contract.get("withdrawn_versions").forEach(version -> withdrawn.add(version.asInt()));
        assertThat(withdrawn).isEqualTo(BridgeForgeGeneManifest.WITHDRAWN_VERSIONS);
        JsonNode evidence = contract.get("evidence_header");
        assertThat(evidence.get("name").asText()).isEqualTo(BridgeForgeEvidenceHeader.EVIDENCE);
        assertThat(evidence.get("schema_version").asInt()).isEqualTo(BridgeForgeEvidenceHeader.SCHEMA_VERSION);
    }

    @Test void everyGeneFixtureIsJudgedAsBridgeForgeJudgesIt() throws Exception {
        JsonNode verdicts = read(CONTRACT.resolve("CONTRACT.json")).get("fixtures");
        assertThat(verdicts.size()).isEqualTo(21);
        Map<String, String> wrong = new TreeMap<>();
        for (var entry : verdicts.properties()) {
            JsonNode gene = read(CONTRACT.resolve("fixtures").resolve(entry.getKey()));
            List<String> problems = BridgeForgeGeneManifest.validate(gene);
            boolean expected = entry.getValue().get("valid").asBoolean();
            if (problems.isEmpty() != expected) {
                wrong.put(entry.getKey(), expected ? String.join("; ", problems) : "accepted");
            } else if (expected) {
                String identity = BridgeForgeGeneManifest.identity(gene.get("kind").asText(), gene.get("members"));
                if (!identity.equals(entry.getValue().get("identity").asText())) {
                    wrong.put(entry.getKey(), "identity " + identity);
                }
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test void everyEvidenceFixtureIsJudgedAsBridgeForgeJudgesIt() throws Exception {
        JsonNode header = read(CONTRACT.resolve("CONTRACT.json")).get("evidence_header");
        JsonNode gene = read(CONTRACT.resolve(header.get("judged_against").asText()));
        assertThat(header.get("fixtures").size()).isEqualTo(8);
        Map<String, String> wrong = new TreeMap<>();
        for (var entry : header.get("fixtures").properties()) {
            List<String> problems = BridgeForgeEvidenceHeader.check(read(CONTRACT.resolve("evidence").resolve(entry.getKey())), gene);
            if (problems.isEmpty() != entry.getValue().get("valid").asBoolean()) {
                wrong.put(entry.getKey(), problems.isEmpty() ? "accepted" : String.join("; ", problems));
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test void aMalformedManifestGivesProblemsNotAnException() throws Exception {
        assertThat(BridgeForgeGeneManifest.validate(JSON.readTree("[]"))).containsExactly("a manifest is a JSON object");
        assertThat(BridgeForgeGeneManifest.validate(JSON.readTree("{}"))).contains("missing schema", "missing members");
        assertThat(BridgeForgeEvidenceHeader.validate(JSON.readTree("1"))).containsExactly("an evidence file is a JSON object");
    }

    /** Each problem with the contract folder against its own CONTRACT.json and the pin. */
    static List<String> contractProblems(Path folder, String pin) throws IOException {
        List<String> problems = new ArrayList<>();
        Path contract = folder.resolve("CONTRACT.json");
        if (!sha256(Files.readAllBytes(contract)).equals(pin)) {
            problems.add("CONTRACT.json differs from the pin");
        }
        for (var file : read(contract).get("files").properties()) {
            Path path = folder.resolve(file.getKey());
            if (!Files.isRegularFile(path) || !sha256(Files.readAllBytes(path)).equals(file.getValue().asText())) {
                problems.add(file.getKey() + " differs from CONTRACT.json");
            }
        }
        return problems;
    }

    private static JsonNode read(Path path) throws IOException {
        return JSON.readTree(Files.readAllBytes(path));
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
