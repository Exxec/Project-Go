package com.ssmt.project;

import static com.ssmt.project.GeneFixtures.JSON;
import static com.ssmt.project.GeneFixtures.gene;
import static com.ssmt.project.GeneFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Project Go P9 item 2, slice 1 (BridgeForge 94.G4): the gene carry report, read only, identities and digests only. */
class BridgeForgeGeneCarryTest {
    private static final String SHIP = "data/hulls/ship_data.csv";
    private static final String DESCRIPTIONS = "data/strings/descriptions.csv";

    @TempDir Path temporary;

    private Path donor() throws Exception {
        Path donor = temporary.resolve("donor");
        write(donor, SHIP, "name,id,designation\nDemo Hull,demo_hull,Cruiser\nOther Hull,other_hull,Frigate\n");
        write(donor, DESCRIPTIONS, "id,type,text1\ndemo_hull,SHIP,A demo hull.\n");
        return donor;
    }

    private static ObjectNode hull() {
        return gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", SHIP + " [demo_hull]"}, {"csv-row", DESCRIPTIONS + " [demo_hull]"}});
    }

    private static ObjectNode carry(ObjectNode gene, Path donor, Path host, LocalizationProject pack) throws Exception {
        ObjectNode export = BridgeForgeGeneStrings.export(List.of(gene), donor, "test");
        return BridgeForgeGeneCarry.report(List.of(gene), export, "a".repeat(64), host, pack, "test");
    }

    @Test
    void aHostWithTheSameStringsCarriesEveryUnitUnchanged() throws Exception {
        Path donor = donor();

        ObjectNode report = carry(hull(), donor, donor, null);

        JsonNode data = report.get("summary").get("data");
        assertThat(data.get("units").asInt()).isEqualTo(3);  // name, designation, text1: other_hull is not this gene's
        assertThat(data.get("UNCHANGED").asInt()).isEqualTo(3);
        assertThat(data.get("carry").get("NOT_EVALUATED").asInt()).isEqualTo(3);
        assertThat(report.get("verdict").asText()).isEqualTo("PASS");
        assertThat(report.get("translations").asText()).startsWith("none");
        assertThat(report.get("gene_strings_sha256").asText()).isEqualTo("a".repeat(64));
        assertThat(BridgeForgeEvidenceHeader.check(report, hull())).isEmpty();
        assertThat(report.get("inputs")).extracting(input -> input.get("path").asText()).containsExactly(SHIP, DESCRIPTIONS);
    }

    @Test
    void changedAddedRemovedAndCjkHostTextAreReportedWithoutText() throws Exception {
        Path donor = donor();
        Path host = temporary.resolve("host");
        // The name is now Chinese (still CJK), a tech/manufacturer cell is new, and the description row is gone.
        write(host, SHIP, "name,id,designation,tech/manufacturer\n演示舰,demo_hull,Cruiser,Demo Works\n");
        write(host, DESCRIPTIONS, "id,type,text1\nother_hull,SHIP,Someone else.\n");

        ObjectNode report = carry(hull(), donor, host, null);

        JsonNode data = report.get("summary").get("data");
        assertThat(data.get("CHANGED").asInt()).isEqualTo(1);
        assertThat(data.get("UNCHANGED").asInt()).isEqualTo(1);
        assertThat(data.get("ADDED").asInt()).isEqualTo(1);
        assertThat(data.get("REMOVED").asInt()).isEqualTo(1);
        assertThat(data.get("host_cjk").asInt()).isEqualTo(1);
        assertThat(report.get("verdict").asText()).isEqualTo("FAIL");
        String serialized = JSON.writeValueAsString(report);
        assertThat(serialized).doesNotContain("Demo Hull", "演示舰", "A demo hull.", "Demo Works", "Cruiser");
        JsonNode removed = null;
        for (JsonNode unit : report.get("genes").get(0).get("units")) {
            if ("REMOVED".equals(unit.get("change").asText())) {
                removed = unit;
            }
        }
        assertThat(removed).isNotNull();
        assertThat(removed.get("file").asText()).isEqualTo(DESCRIPTIONS);
        assertThat(removed.get("host_sha256").asText()).isEmpty();
        assertThat(removed.get("donor_sha256").asText()).hasSize(64);
    }

    @Test
    void aHostDataStringStillInChineseFailsOnItsOwn() throws Exception {
        Path donor = donor();
        Path host = temporary.resolve("host");
        write(host, SHIP, "name,id,designation\n演示舰,demo_hull,Cruiser\n");  // every unit present, one still Chinese
        write(host, DESCRIPTIONS, Files.readString(donor.resolve(DESCRIPTIONS)));

        ObjectNode report = carry(hull(), donor, host, null);

        assertThat(report.get("summary").get("data").get("REMOVED")).isNull();
        assertThat(report.get("summary").get("data").get("host_cjk").asInt()).isEqualTo(1);
        assertThat(report.get("verdict").asText()).isEqualTo("FAIL");
    }

    @Test
    void aPackGivesStringDiffsCarryVerdicts() throws Exception {
        Path donor = donor();
        Path host = temporary.resolve("host");
        write(host, SHIP, "name,id,designation,tech/manufacturer\nDemo Hull Mk2,demo_hull,Cruiser,Demo Works\n");
        write(host, DESCRIPTIONS, "id,type,text1\ndemo_hull,SHIP,A demo hull.\n");
        LocalizationProject pack = new LocalizationProject(1, "demo", "demo.tr", "Demo", List.of(
                new ProjectEntry(Path.of(SHIP), "csv:id=demo_hull:name", "Demo Hull", "Coque demo",
                        TranslationProvenance.HUMAN_EDITED),
                new ProjectEntry(Path.of(SHIP), "csv:id=demo_hull:designation", "Cruiser", "Croiseur",
                        TranslationProvenance.HUMAN_EDITED)));

        ObjectNode report = carry(hull(), donor, host, pack);

        JsonNode carry = report.get("summary").get("data").get("carry");
        assertThat(carry.get("STALE").asInt()).isEqualTo(1);              // the name changed since it was translated
        assertThat(carry.get("CARRIED_OVER").asInt()).isEqualTo(1);       // the designation did not
        assertThat(carry.get("NEEDS_TRANSLATION").asInt()).isEqualTo(2);  // the new cell, and the untranslated description
        assertThat(report.get("translations").asText()).isEqualTo("pack");
        assertThat(report.get("summary").get("data").get("pack_for_other_text").asInt()).isZero();
        assertThat(JSON.writeValueAsString(report)).doesNotContain("Coque demo", "Croiseur");
    }

    @Test
    void aPackWrittenForOtherSourceTextIsFlaggedPerUnit() throws Exception {
        // Found 2026-11-22: a pack written for a mod's original build, given with an export of its translated build, left
        // every such unit NEEDS_TRANSLATION and the report never said the pack did not apply.
        Path donor = donor();
        LocalizationProject pack = new LocalizationProject(1, "demo", "demo.tr", "Demo", List.of(
                new ProjectEntry(Path.of(SHIP), "csv:id=demo_hull:name", "演示舰", "Demo Hull",
                        TranslationProvenance.AI_TRANSLATED),
                new ProjectEntry(Path.of(SHIP), "csv:id=demo_hull:designation", "Cruiser", "Croiseur",
                        TranslationProvenance.HUMAN_EDITED)));

        ObjectNode report = carry(hull(), donor, donor, pack);

        JsonNode data = report.get("summary").get("data");
        assertThat(data.get("pack_for_other_text").asInt()).isEqualTo(1);  // the name: written for 演示舰, not "Demo Hull"
        assertThat(data.get("carry").get("CARRIED_OVER").asInt()).isEqualTo(1);       // the designation's entry applies
        assertThat(data.get("carry").get("NEEDS_TRANSLATION").asInt()).isEqualTo(2);  // the name and the description
        for (JsonNode unit : report.get("genes").get(0).get("units")) {
            assertThat(unit.get("pack_for_other_text").asBoolean())
                    .isEqualTo("csv:id=demo_hull:name".equals(unit.get("key").asText()));
        }
        assertThat(report.get("verdict").asText()).isEqualTo("PASS");  // information, not a failure
        assertThat(JSON.writeValueAsString(report)).doesNotContain("演示舰", "Croiseur");
    }

    @Test
    void withoutAPackNoUnitIsJudgedForOtherText() throws Exception {
        Path donor = donor();

        ObjectNode report = carry(hull(), donor, donor, null);

        assertThat(report.get("summary").get("data").has("pack_for_other_text")).isFalse();
        report.get("genes").get(0).get("units").forEach(unit -> assertThat(unit.has("pack_for_other_text")).isFalse());
    }

    @Test
    void aCsvCellThatDiffersOnlyInLineEndingsIsFlaggedAsTheSameTextToTheGame() throws Exception {
        // The game's CSV parser turns CRLF into LF before reading (RC7 com.fs.starfarer.loading.void.o00000, javap
        // 2026-11-22), so a carrier that writes LF inside a quoted cell changes nothing a player sees.
        Path donor = temporary.resolve("donor");
        write(donor, SHIP, "name,id,designation\r\nDemo Hull,demo_hull,Cruiser\r\n");
        write(donor, DESCRIPTIONS, "id,type,text1\r\ndemo_hull,SHIP,\"First line.\r\nSecond line.\"\r\n");
        Path host = temporary.resolve("host");
        write(host, SHIP, "name,id,designation\nDemo Hull,demo_hull,Cruiser\n");
        write(host, DESCRIPTIONS, "id,type,text1\ndemo_hull,SHIP,\"First line.\nSecond line.\"\n");

        ObjectNode report = carry(hull(), donor, host, null);

        JsonNode data = report.get("summary").get("data");
        assertThat(data.get("CHANGED").asInt()).isEqualTo(1);
        assertThat(data.get("changed_crlf_only").asInt()).isEqualTo(1);
        assertThat(data.get("UNCHANGED").asInt()).isEqualTo(2);
        for (JsonNode unit : report.get("genes").get(0).get("units")) {
            assertThat(unit.get("crlf_only").asBoolean()).isEqualTo(unit.get("file").asText().equals(DESCRIPTIONS));
        }
    }

    @Test
    void theVerdictAndSummaryDoNotDependOnGeneOrder() throws Exception {
        // Review 2026-11-22: two genes share the description unit. In the host the id has a SHIP and a CUSTOM row, so the
        // csv-row gene cannot map its member there (its unit is REMOVED) while the whole-file gene still selects it.
        Path donor = donor();
        Path host = temporary.resolve("host");
        write(host, SHIP, Files.readString(donor.resolve(SHIP)));
        write(host, DESCRIPTIONS, "id,type,text1\ndemo_hull,SHIP,A demo hull.\ndemo_hull,CUSTOM,A place.\n");
        ObjectNode whole = gene("hull", "hull:whole", List.of("hull:whole"), new String[][] {{"file", DESCRIPTIONS}});
        ObjectNode row = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", DESCRIPTIONS + " [demo_hull]"}});
        ObjectNode export = BridgeForgeGeneStrings.export(List.of(whole, row), donor, "test");

        for (List<JsonNode> order : List.<List<JsonNode>>of(List.of(whole, row), List.of(row, whole))) {
            ObjectNode report = BridgeForgeGeneCarry.report(order, export, "x", host, null, "test");

            assertThat(report.get("verdict").asText()).isEqualTo("FAIL");
            assertThat(report.get("summary").get("data").get("REMOVED").asInt()).isEqualTo(1);
        }
    }

    @Test
    void aRemovedUnitIsOrphanedWhenTranslatedAndNoneWhenNot() throws Exception {
        Path donor = donor();
        Path host = temporary.resolve("host");
        write(host, SHIP, "name,id,designation\nDemo Hull,demo_hull,\n");  // the designation is gone
        write(host, DESCRIPTIONS, "id,type,text1\nother_hull,SHIP,Someone else.\n");  // and the description row
        LocalizationProject pack = new LocalizationProject(1, "demo", "demo.tr", "Demo", List.of(
                new ProjectEntry(Path.of(SHIP), "csv:id=demo_hull:designation", "Cruiser", "Croiseur",
                        TranslationProvenance.HUMAN_EDITED)));

        ObjectNode report = carry(hull(), donor, host, pack);

        JsonNode carry = report.get("summary").get("data").get("carry");
        assertThat(carry.get("ORPHANED").asInt()).isEqualTo(1);           // translated, and gone from the host
        assertThat(carry.get("NONE").asInt()).isEqualTo(1);               // gone, never translated
        assertThat(carry.get("NEEDS_TRANSLATION").asInt()).isEqualTo(1);  // the name: present, untranslated
    }

    @Test
    void aMalformedExportUnitIsRefusedNotThrown() throws Exception {
        Path donor = donor();
        ObjectNode export = BridgeForgeGeneStrings.export(List.of(hull()), donor, "test");
        ((ObjectNode) export.get("genes").get(0).get("units").get(0)).remove("file");

        assertThatThrownBy(() -> BridgeForgeGeneCarry.report(List.of(hull()), export, "x", donor, null, "test"))
                .isInstanceOf(ProjectException.class).hasMessageContaining("relative file");
    }

    @Test
    void jarUnitsAreCountedApartAndNotJudged() throws Exception {
        Path donor = donor();
        jar(donor, "Hello pilot");
        Path host = temporary.resolve("host");
        write(host, SHIP, Files.readString(donor.resolve(SHIP)));
        write(host, DESCRIPTIONS, Files.readString(donor.resolve(DESCRIPTIONS)));
        jar(host, "你好");
        ObjectNode gene = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", SHIP + " [demo_hull]"}, {"jar-class", "jars/demo.jar!demo/Plugin.class"}});

        ObjectNode report = carry(gene, donor, host, null);

        JsonNode jar = report.get("summary").get("jar");
        assertThat(jar.get("CHANGED").asInt()).isEqualTo(1);
        assertThat(jar.get("host_cjk").asInt()).isEqualTo(1);
        assertThat(report.get("verdict").asText()).isEqualTo("PASS");  // raw constants: counted, not judged
    }

    @Test
    void anUnreadableHostMemberFileFailsTheReportEvenWhenOnlyJarUnitsAreLost() throws Exception {
        Path donor = donor();
        jar(donor, "Hello pilot");
        Path host = temporary.resolve("host");
        write(host, SHIP, Files.readString(donor.resolve(SHIP)));
        write(host, "jars/demo.jar", "not a jar");  // the bytecode extractor cannot read it
        ObjectNode gene = gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {
            {"csv-row", SHIP + " [demo_hull]"}, {"jar-class", "jars/demo.jar!demo/Plugin.class"}});

        ObjectNode report = carry(gene, donor, host, null);

        assertThat(report.get("unreadable_member_files")).extracting(JsonNode::asText).containsExactly("jars/demo.jar");
        assertThat(report.get("summary").get("data").get("REMOVED")).isNull();
        assertThat(report.get("verdict").asText()).isEqualTo("FAIL");
    }

    @Test
    void anIncompleteExportOrOneAboutOtherGenesIsRefused() throws Exception {
        Path donor = donor();
        ObjectNode export = BridgeForgeGeneStrings.export(List.of(hull()), donor, "test");
        ObjectNode failed = export.deepCopy();
        failed.put("verdict", "FAIL");
        ObjectNode other = gene("hull", "hull:other_hull", List.of("hull:other_hull"), new String[][] {
            {"csv-row", SHIP + " [other_hull]"}});
        ObjectNode notAnExport = export.deepCopy();
        notAnExport.put("format", "something-else");

        assertThatThrownBy(() -> BridgeForgeGeneCarry.report(List.of(hull()), failed, "x", donor, null, "test"))
                .isInstanceOf(ProjectException.class).hasMessageContaining("verdict is FAIL");
        assertThatThrownBy(() -> BridgeForgeGeneCarry.report(List.of(other), export, "x", donor, null, "test"))
                .isInstanceOf(ProjectException.class).hasMessageContaining("other genes");
        assertThatThrownBy(() -> BridgeForgeGeneCarry.report(List.of(hull()), notAnExport, "x", donor, null, "test"))
                .isInstanceOf(ProjectException.class).hasMessageContaining("Not a gene-strings export");
    }

    @Test
    void aMissingHostMemberFileIsListedAndItsUnitsRemoved() throws Exception {
        Path donor = donor();
        Path host = temporary.resolve("host");
        write(host, SHIP, Files.readString(donor.resolve(SHIP)));  // no descriptions.csv in the host

        ObjectNode report = carry(hull(), donor, host, null);

        assertThat(report.get("missing_member_files")).extracting(JsonNode::asText).containsExactly(DESCRIPTIONS);
        assertThat(report.get("summary").get("data").get("REMOVED").asInt()).isEqualTo(1);
        assertThat(report.get("verdict").asText()).isEqualTo("FAIL");
    }

    private static void jar(Path root, String text) throws Exception {
        Path jar = root.resolve("jars/demo.jar");
        Files.createDirectories(Objects.requireNonNull(jar.getParent()));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("demo/Plugin.class"));
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/Plugin", null, "java/lang/Object", null);
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "text", "()Ljava/lang/String;",
                    null, null);
            method.visitCode();
            method.visitLdcInsn(text);
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            writer.visitEnd();
            zip.write(writer.toByteArray());
            zip.closeEntry();
        }
    }
}
