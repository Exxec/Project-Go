package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShipTranslationContextTest {
    @TempDir
    Path directory;

    @Test
    void linksNamesAndDescriptionsAcrossSeparateExportParts() throws Exception {
        ProjectEntry name = entry("data/hulls/ship_data.csv", "csv:id=aDM_huwe:name", "护卫");
        ProjectEntry description = entry("data/strings/descriptions.csv",
                "csv:id%2Ctype=aDM_huwe%00SHIP:text1", "由哨兵改造而来。");
        LocalizationProject project = project(List.of(name, description));
        List<Path> parts = new AiTranslationExchangeService().exportPackage(
                directory.resolve("words.json"), project, "Church", "zh", "en", 1);
        assertThat(parts).hasSize(2);
        for (Path part : parts) {
            var root = new ObjectMapper().readTree(part.toFile());
            assertThat(root.path("entries").size()).isEqualTo(1);
            assertThat(root.path("entries").get(0).path("context").asText())
                    .contains("Ship ID: aDM_huwe", "护卫", "由哨兵改造而来。",
                            name.key(), description.key());
        }
    }

    @Test
    void keepsTypesSubtreesAndEscapedIdsSeparate() {
        ProjectEntry ship = entry("original/data/hulls/ship_data.csv",
                "csv:id=a%3Ab%2525:name", "Ship name");
        ProjectEntry description = entry("original/data/strings/descriptions.csv",
                "csv:id%2Ctype=a%3Ab%2525%00SHIP:text1", "Ship description");
        ProjectEntry weapon = entry("original/data/strings/descriptions.csv",
                "csv:id%2Ctype=a%3Ab%2525%00WEAPON:text1", "Weapon description");
        ProjectEntry other = entry("translated/data/hulls/ship_data.csv",
                ship.key(), "Other subtree");
        ShipTranslationContext context = new ShipTranslationContext(
                project(List.of(ship, description, weapon, other)));
        assertThat(context.forEntry(ship)).contains("Ship ID: a:b%25", "Ship description")
                .doesNotContain("Weapon description", "Other subtree");
        assertThat(context.forEntry(description)).isEqualTo(context.forEntry(ship));
        assertThat(context.forEntry(weapon)).isEmpty();
        assertThat(context.forEntry(other)).doesNotContain("Ship description");
    }

    private static ProjectEntry entry(String file, String key, String source) {
        return new ProjectEntry(Path.of(file), key, source, "");
    }

    private static LocalizationProject project(List<ProjectEntry> entries) {
        return new LocalizationProject(1, "church", "church.en", "Church English", entries);
    }
}
