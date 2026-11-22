package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Project Go P9 item 1 (BridgeForge 94.G2): a gene's strings exported as one unit, as a filter over the existing units. */
class BridgeForgeGeneStringsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HASH = "0".repeat(63) + "1";

    @TempDir Path mod;

    private void writeMod() throws Exception {
        write("data/hulls/ship_data.csv", "name,id,designation,tech/manufacturer\nDemo Hull,demo_hull,Cruiser,Demo Design\n"
                + "Other Hull,other_hull,Frigate,Demo Design\n");
        write("data/hullmods/hull_mods.csv", "name,id,desc,short\nDemo Mod,demo_mod,A demo hull mod.,Demo\n");
        write("data/strings/descriptions.csv", "id,type,text1\ndemo_hull,SHIP,A demo hull.\nother_hull,SHIP,Another hull.\n");
        write("data/config/settings.json", "{\"designTypeColors\": {\"Demo Design\": [1,2,3], \"Other Design\": [4,5,6]}}\n");
        write("data/hulls/demo_hull.ship", "{\"hullId\": \"demo_hull\"}\n");
    }

    private void write(String path, String text) throws Exception {
        Path file = mod.resolve(path);
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static ObjectNode gene(String kind, String anchor, List<String> provides, String[][] members) {
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

    private static ObjectNode demoHull() {
        return gene("hull", "hull:demo_hull", List.of("hull:demo_hull", "hullmod:demo_mod"), new String[][] {
            {"csv-row", "data/hulls/ship_data.csv [demo_hull]"},
            {"csv-row", "data/strings/descriptions.csv [demo_hull]"},
            {"json-entry", "data/config/settings.json#/designTypeColors/Demo Design"},
            {"file", "data/hulls/demo_hull.ship"}});
    }

    private static List<String> keys(JsonNode units) {
        List<String> keys = new ArrayList<>();
        units.forEach(unit -> keys.add(unit.get("file").asText() + " " + unit.get("key").asText()));
        return keys;
    }

    @Test void aGenesMembersSelectTheirOwnUnitsAndNothingElse() throws Exception {
        writeMod();
        ObjectNode gene = demoHull();

        ObjectNode export = BridgeForgeGeneStrings.export(List.of(gene), mod, "test");

        JsonNode entry = export.get("genes").get(0);
        assertThat(keys(entry.get("units"))).containsExactly(
                "data/hulls/ship_data.csv csv:id=demo_hull:designation",
                "data/hulls/ship_data.csv csv:id=demo_hull:name",
                "data/hulls/ship_data.csv csv:id=demo_hull:tech/manufacturer",
                "data/strings/descriptions.csv csv:id%2Ctype=demo_hull%00SHIP:text1",
                "data/config/settings.json json-key:/designTypeColors/Demo Design");
        assertThat(entry.get("units").get(1).get("text").asText()).isEqualTo("Demo Hull");
        assertThat(entry.get("units").get(0).get("member").asText()).isEqualTo("data/hulls/ship_data.csv [demo_hull]");
        // demo_mod is a provided id whose hull_mods.csv row no member selects: a gap in BridgeForge's closure.
        assertThat(keys(entry.get("uncovered"))).containsExactly(
                "data/hullmods/hull_mods.csv csv:id=demo_mod:desc", "data/hullmods/hull_mods.csv csv:id=demo_mod:name",
                "data/hullmods/hull_mods.csv csv:id=demo_mod:short");
        assertThat(entry.get("licence").get("decision").asText()).isEqualTo("LOCAL_ONLY");
        assertThat(export.get("verdict").asText()).isEqualTo("PASS");
        assertThat(export.get("bytecode_catalog").asText()).startsWith("absent");
        assertThat(BridgeForgeEvidenceHeader.check(export, gene)).isEmpty();  // BridgeForge's header, about this gene
        List<String> inputs = new ArrayList<>();
        export.get("inputs").forEach(row -> inputs.add(row.get("path").asText()));
        assertThat(inputs).containsExactly("data/config/settings.json", "data/hulls/demo_hull.ship", "data/hulls/ship_data.csv",
                "data/strings/descriptions.csv");
    }

    @Test void aMissingMemberFileFailsTheExportAndIsListed() throws Exception {
        writeMod();
        Files.delete(mod.resolve("data/hulls/demo_hull.ship"));

        ObjectNode export = BridgeForgeGeneStrings.export(List.of(demoHull()), mod, "test");

        assertThat(export.get("verdict").asText()).isEqualTo("FAIL");
        assertThat(export.get("missing_member_files").get(0).asText()).isEqualTo("data/hulls/demo_hull.ship");
    }

    @Test void anInvalidManifestIsRefused() throws Exception {
        writeMod();
        ObjectNode gene = demoHull();
        gene.put("identity", HASH);

        assertThatThrownBy(() -> BridgeForgeGeneStrings.export(List.of(gene), mod, "test"))
                .isInstanceOf(ProjectException.class).hasMessageContaining("identity does not match");
    }

    @Test void memberRulesMatchProjectGosUnitKeys() {
        assertThat(BridgeForgeGeneStrings.csvRowId("csv:id%2Ctype=ECE1%00SHIP_SYSTEM:text1")).isEqualTo("ECE1");
        assertThat(BridgeForgeGeneStrings.csvRowId("csv:id=a%3Ab:name")).isEqualTo("a:b");
        assertThat(BridgeForgeGeneStrings.csvRowId("json:/name")).isNull();
        assertThat(BridgeForgeGeneStrings.keyedBy("csv:id%2Ctype=x%00SHIP:text1", "id")).isTrue();
        assertThat(BridgeForgeGeneStrings.keyedBy("csv:name=x:desc", "id")).isFalse();
        assertThat(BridgeForgeGeneStrings.selects("jar-class", "jars/a.jar!demo/Plugin.class", "class:demo/Plugin#m()V:0"))
                .isTrue();
        assertThat(BridgeForgeGeneStrings.selects("jar-class", "jars/a.jar!demo/Plugin.class", "class:demo/PluginX#m()V:0"))
                .isFalse();
        assertThat(BridgeForgeGeneStrings.selects("json-entry", "s.json#/a", "json:/ab")).isFalse();
        assertThat(BridgeForgeGeneStrings.selects("json-entry", "s.json#/a", "json:/a/b")).isTrue();
        assertThat(BridgeForgeGeneStrings.memberFile("jar-class", "jars/a.jar!demo/Plugin.class")).isEqualTo("jars/a.jar");
    }

    @Test void reviewCasesAreReportedNotThrown() throws Exception {
        writeMod();
        write("data/missions/demo/descriptor.json", "{\"title\": \"Demo Mission\"}\n");
        write("data/console/commands.csv", "command,help\ndemo_hull,Not about the hull.\n");  // keyed by command
        ObjectNode gene = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", "data/hulls/ship_data.csv [demo_hull]"},
            {"file", "data/missions/demo/descriptor.json"},
            {"file", "data/hulls/demo_hull.ship"},  // no text: a file member is not listed in no_units
            {"csv-row", "data/hulls/ship_data.csv [gone_hull]"},
            {"csv-row", "data/hulls/ship_data.csv"},
            {"jar-class", "jars/demo.jar"},
            {"file", "../outside.txt"}});

        ObjectNode export = BridgeForgeGeneStrings.export(List.of(gene), mod, "test");

        JsonNode entry = export.get("genes").get(0);
        assertThat(keys(entry.get("units"))).contains("data/missions/demo/descriptor.json json:/title");  // a file member
        assertThat(texts(entry.get("not_mappable"))).containsExactly("data/hulls/ship_data.csv", "jars/demo.jar",
                "../outside.txt");
        assertThat(texts(entry.get("no_units"))).containsExactly("data/hulls/ship_data.csv [gone_hull]");
        assertThat(keys(entry.get("uncovered"))).noneMatch(key -> key.startsWith("data/console/commands.csv"));
        assertThat(export.get("verdict").asText()).isEqualTo("PASS");
    }

    @Test void overlappingMembersAreNotNoUnitsAndASharedRowIdIsNotMappable() throws Exception {
        writeMod();
        write("data/strings/descriptions.csv", "id,type,text1\ndemo_hull,SHIP,A demo hull.\ndemo_hull,CUSTOM,A place.\n");
        ObjectNode gene = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"file", "data/hulls/ship_data.csv"},
            {"csv-row", "data/hulls/ship_data.csv [demo_hull]"},  // all its units already selected by the file member
            {"json-entry", "data/config/settings.json#/designTypeColors"},
            {"json-entry", "data/config/settings.json#/designTypeColors/Demo Design"},
            {"csv-row", "data/strings/descriptions.csv [demo_hull]"}});  // two rows: SHIP and CUSTOM

        ObjectNode export = BridgeForgeGeneStrings.export(List.of(gene), mod, "test");

        JsonNode entry = export.get("genes").get(0);
        assertThat(texts(entry.get("no_units"))).isEmpty();
        assertThat(texts(entry.get("not_mappable"))).containsExactly("data/strings/descriptions.csv [demo_hull]");
        assertThat(keys(entry.get("units"))).noneMatch(key -> key.startsWith("data/strings/descriptions.csv"))
                .anyMatch(key -> key.startsWith("data/config/settings.json json-key:/designTypeColors/Demo Design"));
    }

    @Test void aMemberFileTheExtractorCannotReadFailsTheExport() throws Exception {
        writeMod();
        write("data/hulls/ship_data.csv", "name,id\nDemo Hull,demo_hull\n");  // no designation column: not extractable

        ObjectNode export = BridgeForgeGeneStrings.export(List.of(demoHull()), mod, "test");

        assertThat(texts(export.get("unreadable_member_files"))).containsExactly("data/hulls/ship_data.csv");
        assertThat(export.get("verdict").asText()).isEqualTo("FAIL");
    }

    @Test void aJarClassMemberSelectsItsOwnClassesStrings() throws Exception {
        writeMod();
        Path jar = mod.resolve("jars/demo.jar");
        Files.createDirectories(Objects.requireNonNull(jar.getParent()));
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            for (String[] entry : new String[][] {{"demo/Plugin", "Hello pilot"}, {"demo/Other", "Other text"}}) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry[0] + ".class"));
                zip.write(classWith(entry[0], entry[1]));
                zip.closeEntry();
            }
        }
        ObjectNode gene = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", "data/hulls/ship_data.csv [demo_hull]"}, {"jar-class", "jars/demo.jar!demo/Plugin.class"}});

        ObjectNode export = BridgeForgeGeneStrings.export(List.of(gene), mod, "test");

        List<String> jarTexts = new ArrayList<>();
        export.get("genes").get(0).get("units").forEach(unit -> {
            if (unit.get("file").asText().equals("jars/demo.jar")) {
                jarTexts.add(unit.get("text").asText());
            }
        });
        assertThat(jarTexts).containsExactly("Hello pilot");  // not demo/Other's string
        assertThat(export.get("bytecode_catalog").asText()).startsWith("absent");
    }

    private static byte[] classWith(String name, String text) {
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        writer.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC, "text", "()Ljava/lang/String;",
                null, null);
        method.visitCode();
        method.visitLdcInsn(text);
        method.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test void aRowOfATableKeyedByAnotherColumnIsNotMappable() throws Exception {
        writeMod();
        write("data/console/commands.csv", "command,help\ndemo_cmd,Runs the demo.\n");  // Project Go keys it by `command`
        ObjectNode gene = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", "data/hulls/ship_data.csv [demo_hull]"}, {"csv-row", "data/console/commands.csv [demo_cmd]"}});

        JsonNode entry = BridgeForgeGeneStrings.export(List.of(gene), mod, "test").get("genes").get(0);

        assertThat(entry.get("not_mappable").get(0).asText()).isEqualTo("data/console/commands.csv [demo_cmd]");
        assertThat(keys(entry.get("units"))).allMatch(key -> key.startsWith("data/hulls/ship_data.csv"));
    }
}
