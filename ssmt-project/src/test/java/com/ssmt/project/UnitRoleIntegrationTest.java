package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Roles at work: protection, linked groups and the leftover reason. All inputs are synthetic. */
class UnitRoleIntegrationTest {
    private static final String KEY = "新设计";
    @TempDir Path temporary;

    /** A hull CSV whose design type names a key of a colour map, plus a Chinese id column. */
    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/hulls"));
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"roles\",\"name\":\"Roles\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/hulls/ship_data.csv"),
                "id,name,design type\n甲,Plain,新设计\nhull2,名字,新设计\n");
        Files.writeString(mod.resolve("data/config/colors.json"), "{\"designTypeColors\":{\"" + KEY + "\":\"#f00\"}}");
        return mod;
    }

    private static ObjectNode translate(ObjectNode document, Map<String, String> bySourceOrId) {
        for (JsonNode entry : document.path("entries")) {
            String text = bySourceOrId.getOrDefault(entry.path("id").asText(),
                    bySourceOrId.getOrDefault(entry.path("source").asText(), ""));
            ((ObjectNode) entry).put("translation", text);
        }
        return document;
    }

    @Test void applyRefusesToTranslateAProtectedIdentifier() throws Exception {
        Path mod = mod();
        var document = translate(new BridgeForgeTranslationService().export(mod),
                Map.of("甲", "Alpha", "名字", "Name", KEY, "New design"));
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        assertThat(report.path("problems").toString()).contains("csv:data/hulls/ship_data.csv#甲:id")
                .contains("protected identifier");
        String csv = Files.readString(temporary.resolve("out/data/hulls/ship_data.csv"));
        assertThat(csv).contains("甲,Plain").contains("hull2,Name,New design");
        assertThat(report.path("leftover").path("by_reason").path("PROTECTED_IDENTIFIER").asInt()).isEqualTo(1);
    }

    @Test void aLinkedGroupTakesOneTranslationEverywhereOrNoneAtAll() throws Exception {
        Path mod = mod();
        var export = new BridgeForgeTranslationService().export(mod);
        // Only the colour-map key is translated; the design type cells follow it because they share the value.
        var document = translate(export, Map.of("json:data/config/colors.json@0", "New design"));
        var report = new BridgeForgeTranslationApply().apply(mod, document, temporary.resolve("out"), false);
        assertThat(report.path("problems").toString()).doesNotContain("linked");
        assertThat(Files.readString(temporary.resolve("out/data/hulls/ship_data.csv")))
                .contains("hull2,名字,New design");
        assertThat(Files.readString(temporary.resolve("out/data/config/colors.json"))).contains("\"New design\"");

        var conflicting = translate(new BridgeForgeTranslationService().export(mod),
                Map.of("json:data/config/colors.json@0", "One",
                        "csv:data/hulls/ship_data.csv#hull2:design type", "Two"));
        var refused = new BridgeForgeTranslationApply().apply(mod, conflicting, temporary.resolve("out2"), false);
        assertThat(refused.path("problems").toString()).contains("translated inconsistently");
        assertThat(Files.readString(temporary.resolve("out2/data/config/colors.json"))).contains(KEY);
    }

    @Test void projectsOfferNoProtectedIdentifierAndCarryTheRoleOfLinkedUnits() throws Exception {
        Path mod = mod();
        var project = new LocalizationProjectService().createUsingBridgeForge(mod, "p", "P");
        assertThat(project.entries()).noneMatch(entry -> entry.key().contains("#甲:id"));
        var document = project.methodDocument();
        long linked = java.util.stream.StreamSupport.stream(document.path("entries").spliterator(), false)
                .filter(entry -> entry.path("role").asText().equals("LINKED")).count();
        assertThat(linked).isEqualTo(3);
        assertThat(document.path("entries").toString()).contains("\"role\":\"IDENTIFIER\"");
    }

    @Test void builtProjectLeavesTheIdentifierUntouchedAndTranslatesTheLinkedGroupTogether() throws Exception {
        Path mod = mod();
        var service = new LocalizationProjectService();
        var project = service.createUsingBridgeForge(mod, "p", "P");
        var translated = project.withEntries(project.entries().stream()
                .map(entry -> entry.withTranslatedText(entry.originalText().equals(KEY) ? "New design" : "Name"))
                .toList());
        service.build(mod, temporary.resolve("built"), translated);
        String csv = Files.readString(temporary.resolve("built/data/hulls/ship_data.csv"));
        assertThat(csv).contains("甲,Plain,New design").contains("hull2,Name,New design");
        assertThat(Files.readString(temporary.resolve("built/data/config/colors.json"))).contains("New design");
    }

    @Test void importRefusesInconsistentLinkedTranslationsAndCollidingGroups() throws Exception {
        Path mod = mod();
        var workflow = new TranslationWorkflow(temporary.resolve("owned"));
        var session = workflow.loadMod(mod);
        Path response = temporary.resolve("response.json");
        workflow.exportTranslation(session, response);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var document = (ObjectNode) mapper.readTree(response.toFile());
        int toggle = 0;
        for (JsonNode entry : document.path("entries")) {
            boolean member = entry.path("source").asText().equals(KEY);
            ((ObjectNode) entry).put("translation", member ? "Variant " + toggle++ : "Name");
        }
        mapper.writeValue(response.toFile(), document);
        assertThatThrownBy(() -> workflow.importTranslation(session, response))
                .isInstanceOf(ProjectException.class).hasMessageContaining("must share one translation");

        for (JsonNode entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", "Same");
        }
        mapper.writeValue(response.toFile(), document);
        // Plain text may repeat a translation; only LINKED groups must not collide, and there is one group here.
        var completed = workflow.importTranslation(session, response);
        assertThat(completed.project().entries()).allMatch(entry -> entry.translatedText().equals("Same"));
    }

    @Test void checkReportsProtectedIdentifiersAsProtectedNotUntranslated() throws Exception {
        Path mod = mod();
        var remaining = new BridgeForgeTranslationService().export(mod);
        var report = LeftoverClassifier.classify(mod, remaining, null, java.util.List.of());
        assertThat(report.path("by_reason").path("PROTECTED_IDENTIFIER").asInt()).isEqualTo(1);
        assertThat(report.path("by_reason").path("UNTRANSLATED").asInt()).isEqualTo(4);
    }
}
