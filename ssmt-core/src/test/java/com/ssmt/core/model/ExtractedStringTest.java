package com.ssmt.core.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExtractedStringTest {

    @Test
    void rejectsAbsoluteSourcePaths() {
        Path absolute = Path.of("data", "weapons.csv").toAbsolutePath();

        assertThatThrownBy(() -> new ExtractedString("mod", absolute, "row.name", "Text", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void rejectsInvalidLineNumber() {
        assertThatThrownBy(() -> new ExtractedString(
                "mod", Path.of("data", "weapons.csv"), "row.name", "Text", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
