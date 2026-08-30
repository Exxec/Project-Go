package com.ssmt.extractor.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExtractionSpecTest {

    @Test
    void defensivelyCopiesColumns() {
        List<String> columns = new ArrayList<>(List.of("name", "description"));

        CsvExtractionSpec spec = new CsvExtractionSpec("id", columns);
        columns.add("later");

        assertThat(spec.textColumns()).containsExactly("name", "description");
        assertThatThrownBy(() -> spec.textColumns().add("forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsIdentityColumnAsTextColumn() {
        assertThatThrownBy(() -> new CsvExtractionSpec("id", List.of("name", "id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity");
    }

    @Test
    void rejectsDuplicateTextColumns() {
        assertThatThrownBy(() -> new CsvExtractionSpec(
                "id", List.of("description", "description")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void allowsEmptyRequiredTextColumnsWhenOptionalColumnsArePresent() {
        CsvExtractionSpec spec = new CsvExtractionSpec(
                List.of("id"), List.of(), List.of("description"), true);

        assertThat(spec.textColumns()).isEmpty();
        assertThat(spec.allTextColumns()).containsExactly("description");
    }

    @Test
    void rejectsBothTextColumnListsEmpty() {
        assertThatThrownBy(() -> new CsvExtractionSpec(
                List.of("id"), List.of(), List.of(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not both be empty");
    }
}
