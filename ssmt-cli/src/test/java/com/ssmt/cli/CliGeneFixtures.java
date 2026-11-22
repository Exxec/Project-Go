package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.BridgeForgeGeneManifest;
import java.util.List;

/** Synthetic one-row BridgeForge gene manifests for the P9 command tests (never real mod content). */
final class CliGeneFixtures {
    static final ObjectMapper JSON = new ObjectMapper();

    private CliGeneFixtures() { }

    /** A schema-4 manifest of {@code kind} whose one member is the csv-row {@code csvRow}. */
    static ObjectNode gene(String kind, String anchor, String csvRow) {
        ObjectNode gene = JSON.createObjectNode();
        gene.put("schema", BridgeForgeGeneManifest.SCHEMA);
        gene.put("schema_version", BridgeForgeGeneManifest.SCHEMA_VERSION);
        gene.put("kind", kind);
        gene.putArray("anchors").add(anchor);
        gene.putArray("provides").add(anchor);
        ArrayNode members = gene.putArray("members");
        ObjectNode member = members.addObject();
        member.put("what", "csv-row");
        member.put("path", csvRow);
        member.put("sha256", "0".repeat(63) + "1");
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
        gene.put("identity", BridgeForgeGeneManifest.identity(kind, members));
        assertThat(BridgeForgeGeneManifest.validate(gene)).isEmpty();
        return gene;
    }
}
