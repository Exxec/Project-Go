package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Synthetic BridgeForge gene manifests and mod files for the P9 tests (never real mod content). */
final class GeneFixtures {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final String HASH = "0".repeat(63) + "1";

    private GeneFixtures() { }

    /** A schema-4 manifest of {@code kind} with the given members ({what, path}), valid under the pinned contract. */
    static ObjectNode gene(String kind, String anchor, List<String> provides, String[][] members) {
        ObjectNode gene = JSON.createObjectNode();
        gene.put("schema", BridgeForgeGeneManifest.SCHEMA);
        gene.put("schema_version", BridgeForgeGeneManifest.SCHEMA_VERSION);
        gene.put("kind", kind);
        gene.putArray("anchors").add(anchor);
        ArrayNode provided = gene.putArray("provides");
        provides.forEach(provided::add);
        ArrayNode list = gene.putArray("members");
        for (String[] member : members) {
            ObjectNode node = list.addObject();
            node.put("what", member[0]);
            node.put("path", member[1]);
            node.put("sha256", HASH);
        }
        ObjectNode needs = gene.putObject("needs");
        needs.putArray("vanilla");
        needs.putArray("mods");
        for (String key : List.of("registrations", "conditions", "persistent_state", "phenotype_obligations", "collected_from")) {
            gene.putArray(key);
        }
        gene.putObject("build").put("game_version", "0.98.5a-RC7");
        gene.putObject("provenance").put("mod_id", "demo");
        gene.putNull("derived_from");
        ObjectNode licence = gene.putObject("licence");
        licence.put("decision", "LOCAL_ONLY");
        licence.put("mod_decision", "LOCAL_ONLY");
        licence.putArray("local_only_members");
        ObjectNode unknowns = gene.putObject("unknowns");
        unknowns.putArray("missing");
        unknowns.putArray("suspects");
        gene.put("identity", BridgeForgeGeneManifest.identity(kind, list));
        assertThat(BridgeForgeGeneManifest.validate(gene)).isEmpty();
        return gene;
    }

    /** Writes {@code text} as UTF-8 at {@code path} under {@code root}, creating folders. */
    static void write(Path root, String path, String text) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
