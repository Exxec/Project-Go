package com.ssmt.extractor.csv;

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

/**
 * Applies an opt-in schema catalog to exact non-standard CSV files.
 */
public final class ConfiguredCsvFileExtractor implements FileExtractor {
    private final Map<String, CsvExtractionSpec> schemas;

    public ConfiguredCsvFileExtractor(OptInCsvSchema catalog) {
        Objects.requireNonNull(catalog, "catalog must not be null");
        Map<String, CsvExtractionSpec> configured = new LinkedHashMap<>();
        for (OptInCsvFileSchema file : catalog.files()) {
            configured.put(normalize(file.path()), file.toSpec());
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
        CsvExtractionSpec spec = schemas.get(normalize(request.relativeSourceFile()));
        if (spec == null) {
            throw new SsmtParseException(
                    "No opt-in CSV schema for source", request.sourceFile());
        }
        return new CsvExtractor(spec).extract(request);
    }

    private static String normalize(Path path) {
        return path.normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
