package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.BridgeForgeGeneManifest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Project Go P9 item 1 (BridgeForge 94.G2): the gene-strings command over one gene.json or a set proof folder. */
class GeneStringsCommandTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path temporary;

    private Path write(String path, String text) throws Exception {
        Path file = temporary.resolve(path);
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static ObjectNode gene(String kind, String anchor, String csvRow) {
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

    @Test
    void exportsAProofFolderOnceEachSkippingStrayJsonAndRefusesToOverwrite() throws Exception {
        write("mod/data/hulls/ship_data.csv", "name,id,designation\nDemo Hull,demo_hull,Cruiser\n");
        write("mod/data/hullmods/hull_mods.csv", "name,id\nDemo Mod,demo_mod\nOther Mod,other_mod\n");
        ObjectNode hull = gene("hull", "hull:demo_hull", "data/hulls/ship_data.csv [demo_hull]");
        ObjectNode hullMod = gene("hullmod", "hullmod:demo_mod", "data/hullmods/hull_mods.csv [demo_mod]");
        write("proof/gene.json", JSON.writeValueAsString(hull));
        write("proof/closure/a.json", JSON.writeValueAsString(hullMod));
        write("proof/closure/b.JSON", JSON.writeValueAsString(gene("hullmod", "hullmod:other_mod",
                "data/hullmods/hull_mods.csv [other_mod]")));  // an upper-case suffix is read too
        write("proof/closure/c.json", JSON.writeValueAsString(hullMod));  // the same identity again
        write("proof/closure/notes.json", "{\"schema\": \"something-else\"}");  // not a gene manifest
        Path out = temporary.resolve("gene-strings.json");
        String[] arguments = {"gene-strings", temporary.resolve("proof").toString(), temporary.resolve("mod").toString(),
            "--out", out.toString()};

        int exit = new CommandLine(new Main()).execute(arguments);

        JsonNode export = JSON.readTree(out.toFile());
        assertThat(export.get("missing_member_files").toString()).isEqualTo("[]");
        assertThat(export.get("unreadable_member_files").toString()).isEqualTo("[]");
        assertThat(exit).isZero();
        List<String> anchors = new ArrayList<>();
        export.get("genes").forEach(entry -> anchors.add(entry.get("anchor").asText()));
        assertThat(anchors).containsExactly("hull:demo_hull", "hullmod:demo_mod", "hullmod:other_mod");
        assertThat(export.get("produced_by").get("version").asText()).doesNotStartWith("SSMT ");
        assertThat(export.get("verdict").asText()).isEqualTo("PASS");
        String written = Files.readString(out);
        assertThat(new CommandLine(new Main()).execute(arguments)).isEqualTo(1);
        assertThat(Files.readString(out)).isEqualTo(written);
    }

    @Test
    void refusesANonManifestGeneJsonAndAnOutputInsideTheMod() throws Exception {
        write("mod/data/hullmods/hull_mods.csv", "name,id\nDemo Mod,demo_mod\n");
        write("proof/gene.json", "{\"schema\": \"something-else\"}");
        write("proof/closure/a.json", JSON.writeValueAsString(gene("hullmod", "hullmod:demo_mod",
                "data/hullmods/hull_mods.csv [demo_mod]")));
        Path mod = temporary.resolve("mod");
        Path out = temporary.resolve("gene-strings.json");

        int notAManifest = new CommandLine(new Main()).execute("gene-strings", temporary.resolve("proof").toString(),
                mod.toString(), "--out", out.toString());
        int insideTheMod = new CommandLine(new Main()).execute("gene-strings",
                temporary.resolve("proof/closure/a.json").toString(), mod.toString(), "--out",
                mod.resolve("strings.json").toString());

        assertThat(notAManifest).isEqualTo(1);  // the header would otherwise be about the first closure gene
        assertThat(out).doesNotExist();
        assertThat(insideTheMod).isEqualTo(1);
        assertThat(mod.resolve("strings.json")).doesNotExist();
    }

    @Test
    void aMissingMemberFileExitsOne() throws Exception {
        write("mod/data/hulls/other.csv", "name,id\n");
        Path manifest = write("gene.json", JSON.writeValueAsString(gene("hull", "hull:demo_hull",
                "data/hulls/ship_data.csv [demo_hull]")));
        Path out = temporary.resolve("gene-strings.json");

        int exit = new CommandLine(new Main()).execute("gene-strings", manifest.toString(),
                temporary.resolve("mod").toString(), "--out", out.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(JSON.readTree(out.toFile()).get("verdict").asText()).isEqualTo("FAIL");
    }
}
