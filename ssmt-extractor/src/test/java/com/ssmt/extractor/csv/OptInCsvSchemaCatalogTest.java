package com.ssmt.extractor.csv;

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

class OptInCsvSchemaCatalogTest {

    @Test
    void readsExactSchemasAndExtractsOnlySelectedColumns(@TempDir Path root)
            throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/hulls/custom_hull_extra.csv",
                    "identityColumns": ["id"],
                    "textColumns": ["flavorText"]
                  }]
                }
                """);
        OptInCsvSchema schema = new OptInCsvSchemaCatalog().read(catalogFile);
        ConfiguredCsvFileExtractor extractor = new ConfiguredCsvFileExtractor(schema);
        Path source = root.resolve("data/hulls/custom_hull_extra.csv");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                id,flavorText,internalNote
                hull_one,Visible flavor text,internal only
                """);

        List<ExtractedString> extracted =
                extractor.extract(new ExtractionRequest("example", root, source));

        assertThat(extracted).extracting(ExtractedString::key)
                .containsExactly("csv:id=hull_one:flavorText");
        assertThat(extracted).extracting(ExtractedString::originalText)
                .doesNotContain("internal only");
    }

    @Test
    void toleratesMissingOptionalColumnsRatherThanFailing(@TempDir Path root) throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/hulls/custom_hull_extra.csv",
                    "identityColumns": ["id"],
                    "textColumns": ["flavorText", "notPresentColumn"]
                  }]
                }
                """);
        OptInCsvSchema schema = new OptInCsvSchemaCatalog().read(catalogFile);
        ConfiguredCsvFileExtractor extractor = new ConfiguredCsvFileExtractor(schema);
        Path source = root.resolve("data/hulls/custom_hull_extra.csv");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                id,flavorText
                hull_one,Visible flavor text
                """);

        List<ExtractedString> extracted =
                extractor.extract(new ExtractionRequest("example", root, source));

        assertThat(extracted).extracting(ExtractedString::key)
                .containsExactly("csv:id=hull_one:flavorText");
    }

    @Test
    void rejectsUnsafeDuplicateAndStandardPaths(@TempDir Path root) throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {
                  "schemaVersion": 1,
                  "files": [
                    {"path": "../escape.csv", "identityColumns": ["id"], "textColumns": ["name"]},
                    {"path": "data/weapons/weapon_data.csv", "identityColumns": ["id"], "textColumns": ["name"]}
                  ]
                }
                """);

        assertThatThrownBy(() -> new OptInCsvSchemaCatalog().read(catalogFile))
                .isInstanceOf(com.ssmt.core.exception.SsmtParseException.class);
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path root) throws Exception {
        Path catalogFile = root.resolve("schema.json");
        Files.writeString(catalogFile, """
                {"schemaVersion": 2, "files": []}
                """);

        assertThatThrownBy(() -> new OptInCsvSchemaCatalog().read(catalogFile))
                .isInstanceOf(com.ssmt.core.exception.SsmtParseException.class);
    }

    @Test
    void writesDeterministicEditableCatalog(@TempDir Path root) throws Exception {
        OptInCsvSchema schema = new OptInCsvSchema(
                1,
                List.of(
                        new OptInCsvFileSchema(
                                Path.of("data/z.csv"), List.of("id"), List.of("title")),
                        new OptInCsvFileSchema(
                                Path.of("data/a.csv"), List.of("id"), List.of("caption"))));
        Path first = root.resolve("first.json");
        Path second = root.resolve("second.json");
        OptInCsvSchemaCatalog catalog = new OptInCsvSchemaCatalog();

        catalog.write(first, schema);
        catalog.write(second, schema);

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        assertThat(catalog.read(first)).isEqualTo(schema);
    }
}
