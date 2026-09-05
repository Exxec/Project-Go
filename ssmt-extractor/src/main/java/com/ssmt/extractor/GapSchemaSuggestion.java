package com.ssmt.extractor;

import com.ssmt.extractor.csv.OptInCsvFileSchema;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One reviewable result of inferring an opt-in CSV schema for a coverage gap.
 * Suggestions are advisory data for a human decision, never applied directly.
 *
 * @param relativeSourceFile gap file path relative to the mod root
 * @param status inference outcome
 * @param schema inferred schema, present exactly when status is
 *     {@link GapSchemaStatus#SUGGESTED}
 * @param reason evidence sample for a suggestion, or failure detail otherwise
 * @param nonAsciiCellCount data-row cells holding a non-ASCII run, as evidence strength
 */
public record GapSchemaSuggestion(
        Path relativeSourceFile,
        GapSchemaStatus status,
        Optional<OptInCsvFileSchema> schema,
        String reason,
        int nonAsciiCellCount) {

    public GapSchemaSuggestion {
        Objects.requireNonNull(relativeSourceFile, "relativeSourceFile must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if ((status == GapSchemaStatus.SUGGESTED) != schema.isPresent()) {
            throw new IllegalArgumentException(
                    "schema is present exactly when status is SUGGESTED");
        }
        if (nonAsciiCellCount < 0) {
            throw new IllegalArgumentException("nonAsciiCellCount must not be negative");
        }
    }
}
