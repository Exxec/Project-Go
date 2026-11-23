package com.ssmt.project;

import static com.ssmt.project.GeneFixtures.JSON;
import static com.ssmt.project.GeneFixtures.gene;
import static com.ssmt.project.GeneFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gene carry format version 2: the TRANSLATED_IN_HOST and NOT_NEEDED verdicts and their precedence. Synthetic data only. */
class BridgeForgeGeneCarryHostVerdictsTest {
    private static final String SHIP = "data/hulls/ship_data.csv";
    private static final String NAME = "csv:id=demo_hull:name";
    private static final String DESIGNATION = "csv:id=demo_hull:designation";
    private static final String CHINESE = "演示舰";

    @TempDir Path temporary;

    private Path root(String folder, String hullName, String designation) throws Exception {
        Path root = temporary.resolve(folder);
        write(root, SHIP, "name,id,designation\n" + hullName + ",demo_hull," + designation + "\n");
        return root;
    }

    private static ObjectNode hull() {
        return gene("hull", "hull:demo_hull", List.of("hull:demo_hull"), new String[][] {{"csv-row", SHIP + " [demo_hull]"}});
    }

    private static ProjectEntry entry(String key, String original, String translated) {
        return new ProjectEntry(Path.of(SHIP), key, original, translated, TranslationProvenance.HUMAN_EDITED);
    }

    private static LocalizationProject pack(ProjectEntry... entries) {
        return new LocalizationProject(1, "demo", "demo.tr", "Demo", List.of(entries));
    }

    private static ObjectNode carry(Path donor, Path host, LocalizationProject pack) throws Exception {
        ObjectNode export = BridgeForgeGeneStrings.export(List.of(hull()), donor, "test");
        return BridgeForgeGeneCarry.report(List.of(hull()), export, "a".repeat(64), host, pack, "test");
    }

    private static String verdict(ObjectNode report, String key) {
        for (JsonNode unit : report.get("genes").get(0).get("units")) {
            if (key.equals(unit.get("key").asText())) {
                return unit.get("carry").asText();
            }
        }
        throw new AssertionError("no unit " + key);
    }

    @Test
    void aHostHoldingThePacksTranslationReadsTranslatedInHostNotStale() throws Exception {
        Path donor = root("donor", CHINESE, "Cruiser");
        Path host = root("host", "Demo Hull", "Cruiser");

        ObjectNode report = carry(donor, host, pack(entry(NAME, CHINESE, "Demo Hull")));

        assertThat(verdict(report, NAME)).isEqualTo("TRANSLATED_IN_HOST");
        JsonNode counts = report.get("summary").get("data").get("carry");
        assertThat(counts.get("TRANSLATED_IN_HOST").asInt()).isEqualTo(1);
        assertThat(counts.has("STALE")).isFalse();
        assertThat(report.get("verdict").asText()).isEqualTo("PASS");
        assertThat(BridgeForgeEvidenceHeader.check(report, hull())).isEmpty();
        assertThat(report.get("format_version").asInt()).isEqualTo(2);
    }

    @Test
    void aHostTextThatIsNotThePacksTranslationDoesNotReadTranslatedInHost() throws Exception {
        Path donor = root("donor", CHINESE, "Cruiser");
        Path host = root("host", "Demo Hull Mk2", "Cruiser");

        ObjectNode report = carry(donor, host, pack(entry(NAME, CHINESE, "Demo Hull")));

        assertThat(verdict(report, NAME)).isEqualTo("STALE");  // the donor is Chinese, the host is something else
    }

    @Test
    void aChangedStringWhoseHostDiffersFromTheTranslationStaysStale() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");
        Path host = root("host", "Demo Hull Mk2", "Cruiser");

        ObjectNode report = carry(donor, host, pack(entry(NAME, "Demo Hull", "Coque demo")));

        assertThat(verdict(report, NAME)).isEqualTo("STALE");
    }

    @Test
    void aHostStillInChineseIsNeverTranslatedInHostEvenWhenItEqualsTheEntry() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");
        Path host = root("host", CHINESE, "Cruiser");

        ObjectNode report = carry(donor, host, pack(entry(NAME, "Demo Hull", CHINESE)));

        assertThat(verdict(report, NAME)).isEqualTo("STALE");
        assertThat(report.get("summary").get("data").get("host_cjk").asInt()).isEqualTo(1);
        assertThat(report.get("verdict").asText()).isEqualTo("FAIL");
    }

    @Test
    void aDonorStringWithoutCjkReadsNotNeededWhileChineseOnesStillNeedTranslation() throws Exception {
        Path donor = root("donor", CHINESE, "Cruiser");

        ObjectNode report = carry(donor, donor, pack(entry("csv:id=other_hull:name", "Other", "Autre")));

        assertThat(verdict(report, DESIGNATION)).isEqualTo("NOT_NEEDED");
        assertThat(verdict(report, NAME)).isEqualTo("NEEDS_TRANSLATION");
    }

    @Test
    void aDonorStringWithoutCjkWhoseHostTextIsChineseStaysNeedsTranslation() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");
        Path host = root("host", CHINESE, "Cruiser");

        ObjectNode report = carry(donor, host, pack(entry(DESIGNATION, "Cruiser", "Croiseur")));

        assertThat(verdict(report, NAME)).isEqualTo("NEEDS_TRANSLATION");  // flagged host_cjk, not "not needed"
    }

    @Test
    void aPackWrittenForOtherTextOutranksNotNeeded() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");

        ObjectNode report = carry(donor, donor, pack(entry(NAME, CHINESE, "Coque demo")));

        assertThat(verdict(report, NAME)).isEqualTo("NEEDS_TRANSLATION");
        assertThat(verdict(report, DESIGNATION)).isEqualTo("NOT_NEEDED");
    }

    @Test
    void theHostHoldingTheTranslationOutranksTheOtherTextFlagAndNotNeeded() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");
        Path host = root("host", "Demo Hull Mk2", "Cruiser");

        ObjectNode report = carry(donor, host, pack(entry(NAME, CHINESE, "Demo Hull Mk2")));

        assertThat(verdict(report, NAME)).isEqualTo("TRANSLATED_IN_HOST");
    }

    @Test
    void removedAndAddedRowsKeepStringDiffsVerdicts() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");
        Path host = temporary.resolve("host");
        write(host, SHIP, "name,id,designation,tech/manufacturer\nDemo Hull,demo_hull,,Demo Works\n");

        ObjectNode report = carry(donor, host, pack(entry(DESIGNATION, "Cruiser", "Croiseur")));

        assertThat(verdict(report, DESIGNATION)).isEqualTo("ORPHANED");
        assertThat(verdict(report, "csv:id=demo_hull:tech/manufacturer")).isEqualTo("NEEDS_TRANSLATION");  // ADDED: no donor
        assertThat(verdict(report, NAME)).isEqualTo("NOT_NEEDED");
    }

    @Test
    void aCarriedOverRowWhoseHostTextEqualsTheTranslationStaysCarriedOver() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");

        ObjectNode report = carry(donor, donor, pack(entry(NAME, "Demo Hull", "Demo Hull")));  // translated to itself

        assertThat(verdict(report, NAME)).isEqualTo("CARRIED_OVER");
    }

    @Test
    void carriedOverIsNotRewrittenAndNoPackLeavesEveryUnitNotEvaluated() throws Exception {
        Path donor = root("donor", "Demo Hull", "Cruiser");

        ObjectNode withPack = carry(donor, donor, pack(entry(NAME, "Demo Hull", "Coque demo")));
        ObjectNode without = carry(donor, donor, null);

        assertThat(verdict(withPack, NAME)).isEqualTo("CARRIED_OVER");
        assertThat(verdict(without, NAME)).isEqualTo("NOT_EVALUATED");
        assertThat(verdict(without, DESIGNATION)).isEqualTo("NOT_EVALUATED");
        assertThat(JSON.writeValueAsString(withPack)).doesNotContain("Coque demo");
    }
}
