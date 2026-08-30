package com.ssmt.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExtractionRequestTest {

    @Test
    void normalizesPathsAndProvidesRelativeSourcePath() {
        Path root = Path.of("mods", "example").toAbsolutePath();
        Path source = root.resolve("data").resolve("..").resolve("data").resolve("weapons.csv");

        ExtractionRequest request = new ExtractionRequest("example", root, source);

        assertThat(request.modRoot()).isAbsolute().isEqualTo(root.normalize());
        assertThat(request.sourceFile()).isEqualTo(source.normalize());
        assertThat(request.relativeSourceFile()).isEqualTo(Path.of("data", "weapons.csv"));
    }

    @Test
    void rejectsSourceOutsideModRoot() {
        Path root = Path.of("mods", "example").toAbsolutePath();
        Path outside = root.resolveSibling("other").resolve("data.csv");

        assertThatThrownBy(() -> new ExtractionRequest("example", root, outside))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inside modRoot");
    }
}
