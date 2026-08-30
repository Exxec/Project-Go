package com.ssmt.extractor.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptInCsvFileSchemaTest {

    @Test
    void rejectsNonCsvPath() {
        assertThatThrownBy(() -> new OptInCsvFileSchema(
                Path.of("data/config.json"), List.of("id"), List.of("name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSV");
    }

    @Test
    void rejectsAbsoluteOrEscapingPath() {
        assertThatThrownBy(() -> new OptInCsvFileSchema(
                Path.of("../escape.csv"), List.of("id"), List.of("name")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdentityColumnReusedAsTextColumn() {
        assertThatThrownBy(() -> new OptInCsvFileSchema(
                Path.of("data/custom.csv"), List.of("id"), List.of("id")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void treatsDeclaredTextColumnsAsUsedWhenPresent() {
        OptInCsvFileSchema schema = new OptInCsvFileSchema(
                Path.of("data/custom.csv"), List.of("id"), List.of("name", "tooltip"));

        assertThat(schema.identityColumns()).containsExactly("id");
        assertThat(schema.textColumns()).containsExactly("name", "tooltip");
    }
}
