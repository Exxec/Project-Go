package com.ssmt.extractor.csv;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Versioned bounded custom CSV extraction catalog.
 *
 * @param schemaVersion catalog schema version
 * @param files exact file schemas
 */
public record OptInCsvSchema(int schemaVersion, List<OptInCsvFileSchema> files) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public OptInCsvSchema {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported opt-in CSV schema version " + schemaVersion);
        }
        files = List.copyOf(files).stream()
                .sorted(Comparator.comparing(file -> normalize(file.path())))
                .toList();
        if (files.size() > 256) {
            throw new IllegalArgumentException("Schema file count exceeds limit");
        }
        Set<String> paths = new HashSet<>();
        for (OptInCsvFileSchema file : files) {
            String normalized = normalize(file.path());
            if (!paths.add(normalized)) {
                throw new IllegalArgumentException("Duplicate schema path " + file.path());
            }
            if (StandardCsvSchemas.matchesSuffix(file.path())) {
                throw new IllegalArgumentException(
                        "Opt-in schema overlaps standard handler " + file.path());
            }
        }
    }

    @Override
    public List<OptInCsvFileSchema> files() {
        return List.copyOf(files);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
