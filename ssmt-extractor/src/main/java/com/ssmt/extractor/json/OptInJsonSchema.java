package com.ssmt.extractor.json;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versioned bounded custom JSON extraction catalog.
 *
 * @param schemaVersion catalog schema version
 * @param files exact file schemas
 */
public record OptInJsonSchema(int schemaVersion, List<OptInJsonFileSchema> files) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public OptInJsonSchema {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported opt-in JSON schema version " + schemaVersion);
        }
        files = List.copyOf(files).stream()
                .sorted(Comparator.comparing(file -> normalize(file.path())))
                .toList();
        if (files.size() > 256) {
            throw new IllegalArgumentException("Schema file count exceeds limit");
        }
        Set<String> paths = new HashSet<>();
        StandardJsonFileExtractor standards = new StandardJsonFileExtractor();
        for (OptInJsonFileSchema file : files) {
            String normalized = normalize(file.path());
            if (!paths.add(normalized)) {
                throw new IllegalArgumentException("Duplicate schema path " + file.path());
            }
            if (standards.supports(file.path())) {
                throw new IllegalArgumentException(
                        "Opt-in schema overlaps standard handler " + file.path());
            }
        }
    }

    @Override
    public List<OptInJsonFileSchema> files() {
        return List.copyOf(files);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
    }
}
