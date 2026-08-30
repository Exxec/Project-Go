package com.ssmt.gui;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.extractor.json.OptInJsonFileSchema;
import com.ssmt.extractor.json.OptInJsonSchema;
import com.ssmt.extractor.json.OptInJsonSchemaCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editable state for exact-path custom JSON extraction schemas.
 */
public final class JsonSchemaEditorViewModel {
    private final Map<Path, List<String>> files = new LinkedHashMap<>();

    /**
     * Adds a selected RFC 6901 pointer to an exact safe relative file.
     *
     * @param path exact source-relative JSON-like path
     * @param pointer selected pointer
     */
    public void add(Path path, String pointer) {
        OptInJsonFileSchema validated =
                new OptInJsonFileSchema(path, List.of(pointer));
        List<String> pointers = new ArrayList<>(
                files.getOrDefault(validated.path(), List.of()));
        if (!pointers.contains(pointer)) {
            pointers.add(pointer);
            pointers.sort(String::compareTo);
        }
        files.put(validated.path(), List.copyOf(pointers));
    }

    /**
     * Removes one selected pointer.
     *
     * @param path exact path
     * @param pointer selected pointer
     */
    public void remove(Path path, String pointer) {
        List<String> pointers = new ArrayList<>(files.getOrDefault(path, List.of()));
        pointers.remove(pointer);
        if (pointers.isEmpty()) {
            files.remove(path);
        } else {
            files.put(path, List.copyOf(pointers));
        }
    }

    /**
     * @return validated deterministic schema
     */
    public OptInJsonSchema schema() {
        return new OptInJsonSchema(
                OptInJsonSchema.CURRENT_SCHEMA_VERSION,
                files.entrySet().stream()
                        .map(entry -> new OptInJsonFileSchema(
                                entry.getKey(), entry.getValue()))
                        .toList());
    }

    /**
     * Writes current schema to a user-owned document.
     *
     * @param destination catalog destination
     * @throws SsmtParseException on validation or write failure
     */
    public void save(Path destination) throws SsmtParseException {
        new OptInJsonSchemaCatalog().write(destination, schema());
    }
}
