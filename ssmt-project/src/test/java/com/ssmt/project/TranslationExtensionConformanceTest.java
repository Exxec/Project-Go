package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationExtensionConformanceTest {
    @TempDir Path temporary;

    private JsonNode fixture() throws Exception {
        return fixture("schema-v1-extensions.json");
    }

    private JsonNode fixture(String name) throws Exception {
        return new ObjectMapper().readTree(Path.of("../fixtures/translation-conformance", name).toFile());
    }

    @Test void extendedSchemaV1MatchesReferenceUnitsHashesAndEveryOutputByte() throws Exception {
        verifyFixture("schema-v1-extensions.json");
    }

    @Test void importedRolesPreserveIdentifiersAndTranslateTheCompleteGroup() throws Exception {
        verifyFixture("schema-v1-roles.json");
    }

    private void verifyFixture(String name) throws Exception {
        JsonNode fixture = fixture(name);
        Path source = temporary.resolve("source");
        var fields = fixture.path("input_files").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            Path path = source.resolve(field.getKey());
            Files.createDirectories(java.util.Objects.requireNonNull(path.getParent()));
            Files.write(path, Base64.getDecoder().decode(field.getValue().asText()));
        }
        var before = new HashMap<Path, byte[]>();
        try (var walk = Files.walk(source)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                before.put(path, Files.readAllBytes(path));
            }
        }
        var service = new BridgeForgeTranslationService();
        var actual = service.export(source);
        var expected = fixture.path("expected_export").fields();
        while (expected.hasNext()) {
            var field = expected.next();
            assertThat(actual.path(field.getKey())).as(field.getKey()).isEqualTo(field.getValue());
        }
        ObjectNode translated = (ObjectNode) fixture.path("translated");
        new BridgeForgeTranslationDocument().validate(translated);
        Path output = temporary.resolve("output");
        var report = new BridgeForgeTranslationApply().apply(source, translated, output, false);
        assertThat(report.path("problems").size()).isEqualTo(fixture.path("expected_problem_count").asInt());
        var outputFiles = new HashMap<String, String>();
        try (var walk = Files.walk(output)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                outputFiles.put(output.relativize(path).toString().replace('\\', '/'),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        JsonNode actualFiles = new ObjectMapper().valueToTree(outputFiles);
        assertThat(actualFiles).isEqualTo(fixture.path("expected_output_files"));
        for (var file : before.entrySet()) {
            assertThat(Files.readAllBytes(file.getKey())).isEqualTo(file.getValue());
        }
    }

    @Test void invalidRoleOrGroupMetadataCannotEnterAnExchangeDocument() throws Exception {
        var validator = new BridgeForgeTranslationDocument();
        for (String role : new String[] {"UNSAFE", "LINKED"}) {
            var document = (ObjectNode) fixture().path("translated");
            ((ObjectNode) document.path("entries").get(0)).put("role", role);
            assertThatThrownBy(() -> validator.validate(document)).isInstanceOf(IllegalArgumentException.class);
        }
        var document = (ObjectNode) fixture().path("translated");
        ((ObjectNode) document.path("entries").get(0)).put("group", "unexpected");
        assertThatThrownBy(() -> validator.validate(document)).isInstanceOf(IllegalArgumentException.class);
    }
}
