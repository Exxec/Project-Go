package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeForgeTranslationComparisonTest {
    private static final Path FIXTURE = Path.of("../fixtures/translation-conformance");
    private final BridgeForgeTranslationComparison comparison = new BridgeForgeTranslationComparison();
    @TempDir Path temporary;

    private ObjectNode reference() throws Exception {
        return new BridgeForgeTranslationDocument().read(FIXTURE.resolve("expected-export.json"));
    }

    @Test void matchesReferenceAndIgnoresTranslationAndOrder() throws Exception {
        var reference = reference();
        var entries = (ArrayNode) reference.path("entries");
        var first = (ObjectNode) entries.remove(0);
        first.put("translation", "Different translation");
        entries.add(first);
        var report = comparison.compare(FIXTURE.resolve("input"), reference);
        assertThat(report.path("status").asText()).isEqualTo("MATCH");
        assertThat(report.path("project_go_units").asInt()).isEqualTo(18);
        assertThat(report.path("differences")).isEmpty();
    }

    @Test void reportsMissingExtraAndChangedUnitsDeterministically() throws Exception {
        var reference = reference();
        var entries = (ArrayNode) reference.path("entries");
        var renamed = (ObjectNode) entries.get(0);
        renamed.put("id", "reference-only-unit");
        ((ObjectNode) entries.get(1)).put("source", "Changed protected text");
        var report = comparison.compare(FIXTURE.resolve("input"), reference);
        assertThat(report.path("status").asText()).isEqualTo("DIFFERENCES");
        assertThat(report.path("differences").size()).isEqualTo(3);
        assertThat(report.toString()).contains("MISSING_IN_PROJECT_GO", "EXTRA_IN_PROJECT_GO",
                "PROTECTED_UNIT_CHANGED", "reference_file");
        assertThat(comparison.compare(FIXTURE.resolve("input"), reference)).isEqualTo(report);
    }

    @Test void rejectsStaleHashesEvenForUnitsAbsentFromCurrentDiscovery() throws Exception {
        var reference = reference();
        ((ObjectNode) reference.path("file_hashes")).put("missing.json", "0".repeat(64));
        var report = comparison.compare(FIXTURE.resolve("input"), reference);
        assertThat(report.path("status").asText()).isEqualTo("SOURCE_MISMATCH");
        assertThat(report.path("source_mismatches").toString()).contains("missing.json");
    }

    @Test void doesNotClaimParityWithUnreadableReferenceOrWrongMod() throws Exception {
        var reference = reference();
        ((ArrayNode) reference.path("unreadable")).add("unreadable.jar: CRC mismatch");
        assertThat(comparison.compare(FIXTURE.resolve("input"), reference).path("status").asText())
                .isEqualTo("INCOMPLETE");
        reference.put("mod_id", "another.mod");
        assertThat(comparison.compare(FIXTURE.resolve("input"), reference).path("status").asText())
                .isEqualTo("SOURCE_MISMATCH");
    }

    @Test void writesOnlyNewExternalReportsAndPreservesSources() throws Exception {
        Path source = FIXTURE.resolve("input");
        var before = new BridgeForgeTranslationService().export(source);
        var report = comparison.compare(source, reference());
        Path output = temporary.resolve("comparison.json");
        comparison.write(source, output, report);
        byte[] bytes = Files.readAllBytes(output);
        assertThatThrownBy(() -> comparison.write(source, output, report)).isInstanceOf(ProjectException.class);
        assertThat(Files.readAllBytes(output)).isEqualTo(bytes);
        assertThatThrownBy(() -> comparison.write(source, source.resolve("comparison.json"), report))
                .isInstanceOf(ProjectException.class).hasMessageContaining("outside the source");
        assertThat(new BridgeForgeTranslationService().export(source)).isEqualTo(before);
    }
}
