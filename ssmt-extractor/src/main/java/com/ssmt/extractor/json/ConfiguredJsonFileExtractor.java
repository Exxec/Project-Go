package com.ssmt.extractor.json;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies an opt-in schema catalog to exact non-standard JSON-like files.
 */
public final class ConfiguredJsonFileExtractor implements FileExtractor {
    private final Map<String, JsonExtractionSpec> schemas;

    public ConfiguredJsonFileExtractor(OptInJsonSchema catalog) {
        Objects.requireNonNull(catalog, "catalog must not be null");
        Map<String, JsonExtractionSpec> configured = new LinkedHashMap<>();
        for (OptInJsonFileSchema file : catalog.files()) {
            configured.put(
                    normalize(file.path()),
                    JsonExtractionSpec.selectedPointers(Set.copyOf(file.pointers())));
        }
        schemas = Map.copyOf(configured);
    }

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        String normalized = normalize(sourceFile);
        return schemas.keySet().stream().anyMatch(schemaPath ->
                normalized.equals(schemaPath) || normalized.endsWith("/" + schemaPath));
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        Objects.requireNonNull(request, "request must not be null");
        JsonExtractionSpec spec = schemas.get(normalize(request.relativeSourceFile()));
        if (spec == null) {
            throw new SsmtParseException(
                    "No opt-in JSON schema for source", request.sourceFile());
        }
        return new JsonExtractor(spec).extract(request);
    }

    private static String normalize(Path path) {
        return path.normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
