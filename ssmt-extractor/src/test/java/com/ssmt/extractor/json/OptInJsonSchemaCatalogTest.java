package com.ssmt.extractor.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptInJsonSchemaCatalogTest {

    @Test
    void readsExactSchemasAndExtractsOnlySelectedPointers(@TempDir Path root)
            throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/config/custom.json",
                    "pointers": ["/description", "/nested/title"]
                  }]
                }
                """);
        OptInJsonSchema schema = new OptInJsonSchemaCatalog().read(catalogFile);
        ConfiguredJsonFileExtractor extractor = new ConfiguredJsonFileExtractor(schema);
        Path source = root.resolve("data/config/custom.json");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  "id": "internal",
                  "description": "Visible description",
                  "nested": {"title": "Visible title", "asset": "graphics/a.png"}
                }
                """);

        List<ExtractedString> extracted =
                extractor.extract(new ExtractionRequest("example", root, source));

        assertThat(extracted).extracting(ExtractedString::key)
                .containsExactly("json:/description", "json:/nested/title");
        assertThat(extracted).extracting(ExtractedString::originalText)
                .doesNotContain("internal", "graphics/a.png");
    }

    @Test
    void rejectsUnsafeDuplicateAndStandardPaths(@TempDir Path root) throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {
                  "schemaVersion": 1,
                  "files": [
                    {"path": "../escape.json", "pointers": ["/name"]},
                    {"path": "data/strings/strings.json", "pointers": ["/name"]}
                  ]
                }
                """);

        assertThatThrownBy(() -> new OptInJsonSchemaCatalog().read(catalogFile))
                .isInstanceOf(com.ssmt.core.exception.SsmtParseException.class);
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path root) throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {"schemaVersion": 2, "files": []}
                """);

        assertThatThrownBy(() -> new OptInJsonSchemaCatalog().read(catalogFile))
                .isInstanceOf(com.ssmt.core.exception.SsmtParseException.class);
    }

    @Test
    void writesDeterministicEditableCatalog(@TempDir Path root) throws Exception {
        OptInJsonSchema schema = new OptInJsonSchema(
                1,
                List.of(
                        new OptInJsonFileSchema(
                                Path.of("data/z.json"), List.of("/title")),
                        new OptInJsonFileSchema(
                                Path.of("data/a.json"), List.of("/caption"))));
        Path first = root.resolve("first.json");
        Path second = root.resolve("second.json");
        OptInJsonSchemaCatalog catalog = new OptInJsonSchemaCatalog();

        catalog.write(first, schema);
        catalog.write(second, schema);

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        assertThat(catalog.read(first)).isEqualTo(schema);
    }
}
