package com.ssmt.extractor.csv;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Conservative schemas for standard Starsector CSV locations.
 */
public final class StandardCsvSchemas {

    private static final Map<String, CsvExtractionSpec> SCHEMAS = Map.ofEntries(
            Map.entry("data/strings/descriptions.csv",
                    new CsvExtractionSpec(
                            List.of("id", "type"),
                            List.of("text1"),
                            List.of("text2", "text3", "text4"),
                            true)),
            Map.entry("data/weapons/weapon_data.csv",
                    new CsvExtractionSpec(List.of("id"), List.of("name"),
                            List.of("tech/manufacturer", "primaryRoleStr", "customAncillaryHL"), true)),
            Map.entry("data/hulls/ship_data.csv",
                    new CsvExtractionSpec("id", List.of("name", "designation"),
                            List.of("tech/manufacturer"))),
            Map.entry("data/shipsystems/ship_systems.csv",
                    new CsvExtractionSpec("id", List.of("name"))),
            Map.entry("data/hullmods/hull_mods.csv",
                    new CsvExtractionSpec("id", List.of("name"),
                            List.of("tech/manufacturer", "desc", "short"))),
            Map.entry("data/characters/skills/skill_data.csv",
                    new CsvExtractionSpec("id", List.of("name", "description"), List.of("author"))),
            Map.entry("data/hulls/wing_data.csv",
                    new CsvExtractionSpec(
                            List.of("id"), List.of(), List.of("role desc"), true)),
            Map.entry("data/campaign/commodities.csv",
                    new CsvExtractionSpec("id", List.of("name"), List.of("desc"))),
            Map.entry("data/campaign/industries.csv",
                    new CsvExtractionSpec("id", List.of("name"), List.of("desc"))),
            Map.entry("data/campaign/market_conditions.csv",
                    new CsvExtractionSpec("id", List.of("name"), List.of("desc"))),
            Map.entry("data/campaign/special_items.csv",
                    new CsvExtractionSpec("id", List.of("name"),
                            List.of("tech/manufacturer", "desc"))),
            Map.entry("data/campaign/submarkets.csv",
                    new CsvExtractionSpec("id", List.of("name"), List.of("desc"))),
            // Vanilla Starsector's campaign-rules table (RelationshipLevelDesc and
            // similar rows carry the actual dialogue/description text shown to the
            // player in the "text" column; "options" is a structured mini-language
            // in vanilla and was never observed non-blank, so it stays unverified;
            // "notes" is a developer-only comment field). Found missing via a real
            // mod's `ssmt extract` diagnostic (AzureFederation).
            Map.entry("data/campaign/rules.csv",
                    new CsvExtractionSpec("id", List.of("text"))),
            // Nexerelin's (a declared, widely-used companion mod) own custom-start
            // background format. Found missing the same way.
            Map.entry("data/config/exerelin/character_backgrounds.csv",
                    new CsvExtractionSpec("id", List.of("name"),
                            List.of("shortDescription", "longDescription"))));

    private StandardCsvSchemas() {
    }

    /**
     * Finds a schema by normalized mod-relative path.
     *
     * @param relativePath path relative to a mod root
     * @return matching schema, when the path is standard
     */
    public static Optional<CsvExtractionSpec> find(Path relativePath) {
        String normalized = normalize(relativePath);
        return SCHEMAS.entrySet().stream()
                .filter(entry -> normalized.equals(entry.getKey())
                        || normalized.endsWith("/" + entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    static boolean matchesSuffix(Path sourceFile) {
        String normalized = normalize(sourceFile);
        return SCHEMAS.keySet().stream()
                .anyMatch(path -> normalized.equals(path) || normalized.endsWith("/" + path));
    }

    private static String normalize(Path path) {
        return path.normalize()
                .toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
    }
}
