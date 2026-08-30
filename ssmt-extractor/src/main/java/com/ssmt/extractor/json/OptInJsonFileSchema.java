package com.ssmt.extractor.json;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exact custom JSON-like file and its selected textual pointers.
 *
 * @param path safe relative source path
 * @param pointers selected RFC 6901 pointers
 */
public record OptInJsonFileSchema(Path path, List<String> pointers) {
    public OptInJsonFileSchema {
        if (path == null || path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("Schema path must be safe and relative");
        }
        path = path.normalize();
        String lower = path.toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".json")
                || lower.endsWith(".faction")
                || lower.endsWith(".variant"))) {
            throw new IllegalArgumentException("Schema path must be JSON-like");
        }
        pointers = JsonExtractionSpec.selectedPointers(Set.copyOf(pointers)).pointers();
        if (pointers.size() > 256) {
            throw new IllegalArgumentException("Schema pointer count exceeds limit");
        }
    }

    @Override
    public List<String> pointers() {
        return List.copyOf(pointers);
    }
}
