package com.ssmt.extractor.csv;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Exact custom CSV file and its identity/text columns.
 *
 * @param path safe relative source path
 * @param identityColumns ordered columns whose combined values identify a row
 * @param textColumns columns used when present; missing columns are skipped, not errors
 */
public record OptInCsvFileSchema(Path path, List<String> identityColumns, List<String> textColumns) {
    public OptInCsvFileSchema {
        if (path == null || path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("Schema path must be safe and relative");
        }
        path = path.normalize();
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Schema path must be a CSV file");
        }
        // Reuses CsvExtractionSpec's identity/text-column validation (blank,
        // duplicate, and identity/text overlap checks) as the single source
        // of truth for column-name legality.
        CsvExtractionSpec spec = new CsvExtractionSpec(
                identityColumns, List.of(), textColumns, true);
        identityColumns = spec.identityColumns();
        textColumns = spec.optionalTextColumns();
        if (textColumns.size() > 256) {
            throw new IllegalArgumentException("Schema column count exceeds limit");
        }
    }

    @Override
    public List<String> identityColumns() {
        return List.copyOf(identityColumns);
    }

    @Override
    public List<String> textColumns() {
        return List.copyOf(textColumns);
    }

    /**
     * Builds the extraction spec this schema describes.
     *
     * @return equivalent conservative CSV extraction spec
     */
    CsvExtractionSpec toSpec() {
        return new CsvExtractionSpec(identityColumns, List.of(), textColumns, true);
    }
}
