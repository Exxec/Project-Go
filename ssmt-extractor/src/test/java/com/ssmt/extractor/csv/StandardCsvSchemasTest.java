package com.ssmt.extractor.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StandardCsvSchemasTest {
    @Test
    void resolvesStandardSchemaInsideExplicitParallelNamespace() {
        assertThat(StandardCsvSchemas.find(
                Path.of("aEP/data/strings/descriptions.csv"))).isPresent();
        assertThat(StandardCsvSchemas.find(
                Path.of("aEP_En/data/strings/descriptions.csv"))).isPresent();
    }

    @Test
    void definesConservativeStandardStarsectorSchemas() {
        assertThat(StandardCsvSchemas.find(Path.of("data/strings/descriptions.csv")))
                .hasValue(new CsvExtractionSpec(
                        java.util.List.of("id", "type"),
                        java.util.List.of("text1"),
                        java.util.List.of("text2", "text3", "text4"),
                        true));
        assertThat(StandardCsvSchemas.find(Path.of("data/weapons/weapon_data.csv")))
                .hasValue(new CsvExtractionSpec(
                        java.util.List.of("id"),
                        java.util.List.of("name"),
                        java.util.List.of("tech/manufacturer", "primaryRoleStr", "customAncillaryHL"),
                        true));
        assertThat(StandardCsvSchemas.find(Path.of("data/hulls/ship_data.csv")))
                .hasValue(new CsvExtractionSpec(
                        "id", java.util.List.of("name", "designation"),
                        java.util.List.of("tech/manufacturer")));
        assertThat(StandardCsvSchemas.find(Path.of("data/shipsystems/ship_systems.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name")));
        assertThat(StandardCsvSchemas.find(Path.of("data/hullmods/hull_mods.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name"),
                        java.util.List.of("tech/manufacturer", "desc", "short")));
        assertThat(StandardCsvSchemas.find(Path.of("data/characters/skills/skill_data.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name", "description"),
                        java.util.List.of("author")));
        assertThat(StandardCsvSchemas.find(Path.of("data/hulls/wing_data.csv")))
                .hasValue(new CsvExtractionSpec(java.util.List.of("id"), java.util.List.of(),
                        java.util.List.of("role desc"), true));
        assertThat(StandardCsvSchemas.find(Path.of("data/campaign/commodities.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name"),
                        java.util.List.of("desc")));
        assertThat(StandardCsvSchemas.find(Path.of("data/campaign/industries.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name"),
                        java.util.List.of("desc")));
        assertThat(StandardCsvSchemas.find(Path.of("data/campaign/market_conditions.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name"),
                        java.util.List.of("desc")));
        assertThat(StandardCsvSchemas.find(Path.of("data/campaign/special_items.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name"),
                        java.util.List.of("tech/manufacturer", "desc")));
        assertThat(StandardCsvSchemas.find(Path.of("data/campaign/submarkets.csv")))
                .hasValue(new CsvExtractionSpec("id", java.util.List.of("name"),
                        java.util.List.of("desc")));
    }

    @Test
    void normalizesSeparatorsAndCaseButRejectsLookalikePaths() {
        assertThat(StandardCsvSchemas.find(
                Path.of("DATA", "WEAPONS", "WEAPON_DATA.CSV"))).isPresent();
        assertThat(StandardCsvSchemas.find(Path.of("backup/weapon_data.csv"))).isEmpty();
        assertThat(StandardCsvSchemas.find(Path.of("data/weapons/weapons.csv"))).isEmpty();
    }
}
