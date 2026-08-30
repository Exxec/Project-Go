package com.ssmt.extractor.csv;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declares how one CSV layout identifies rows and localizable cells.
 *
 * @param identityColumns columns whose combined values remain stable across row movement
 * @param textColumns ordered required localizable columns
 * @param optionalTextColumns ordered localizable columns used when present
 */
public record CsvExtractionSpec(
        List<String> identityColumns,
        List<String> textColumns,
        List<String> optionalTextColumns,
        boolean skipUnidentifiedRows
) {

    public CsvExtractionSpec(String identityColumn, List<String> textColumns) {
        this(List.of(identityColumn), textColumns, List.of(), false);
    }

    public CsvExtractionSpec(
            String identityColumn,
            List<String> textColumns,
            List<String> optionalTextColumns
    ) {
        this(List.of(identityColumn), textColumns, optionalTextColumns, false);
    }

    public CsvExtractionSpec(
            List<String> identityColumns,
            List<String> textColumns,
            List<String> optionalTextColumns
    ) {
        this(identityColumns, textColumns, optionalTextColumns, false);
    }

    public CsvExtractionSpec {
        Objects.requireNonNull(identityColumns, "identityColumns must not be null");
        Objects.requireNonNull(textColumns, "textColumns must not be null");
        Objects.requireNonNull(optionalTextColumns, "optionalTextColumns must not be null");
        identityColumns = List.copyOf(identityColumns);
        if (identityColumns.isEmpty()
                || identityColumns.stream().anyMatch(column -> column == null || column.isBlank())) {
            throw new IllegalArgumentException("identityColumns must not be empty or blank");
        }
        textColumns = List.copyOf(textColumns);
        optionalTextColumns = List.copyOf(optionalTextColumns);
        if (textColumns.isEmpty() && optionalTextColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "textColumns and optionalTextColumns must not both be empty");
        }

        Set<String> unique = new HashSet<>();
        for (String column : textColumns) {
            Objects.requireNonNull(column, "text column must not be null");
            if (column.isBlank()) {
                throw new IllegalArgumentException("text column must not be blank");
            }
            if (identityColumns.contains(column)) {
                throw new IllegalArgumentException("identity column cannot be a text column");
            }
            if (!unique.add(column)) {
                throw new IllegalArgumentException("duplicate text column: " + column);
            }
        }
        for (String column : optionalTextColumns) {
            Objects.requireNonNull(column, "optional text column must not be null");
            if (column.isBlank() || identityColumns.contains(column) || !unique.add(column)) {
                throw new IllegalArgumentException("invalid optional text column: " + column);
            }
        }
    }

    public List<String> allTextColumns() {
        java.util.ArrayList<String> columns = new java.util.ArrayList<>(textColumns);
        columns.addAll(optionalTextColumns);
        return List.copyOf(columns);
    }

    public String identityColumn() {
        return identityColumns.getFirst();
    }
}
