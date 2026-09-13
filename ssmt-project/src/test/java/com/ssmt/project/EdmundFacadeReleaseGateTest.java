package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Permanent release gate: exact supplied corpus through the shared normal workflow. */
class EdmundFacadeReleaseGateTest {
    @TempDir Path directory;

    @Test void discovers187TranslationsRefreshes213AndPreservesThemAcrossRestartAndExport() throws Exception {
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/edmund/workflow.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName().replace('\\', '/');
                Path target = directory.resolve(name).normalize();
                assertThat(target.startsWith(directory)).isTrue();
                if (!name.endsWith("/")) {
                    Files.createDirectories(java.util.Objects.requireNonNull(target.getParent()));
                    Files.copy(zip, target);
                }
            }
        }
        var json = new ObjectMapper();
        var response = json.readTree(directory.resolve("translated-187.json").toFile());
        var entries = new ArrayList<ProjectEntry>();
        var expected = new HashMap<String, String>();
        for (var item : response.path("entries")) {
            entries.add(new ProjectEntry(Path.of(item.path("relativeFilePath").asText()),
                    item.path("internalId").asText(), item.path("source").asText(), item.path("translation").asText()));
            expected.put(item.path("id").asText(), item.path("translation").asText());
        }
        assertThat(entries).hasSize(187);
        Path legacy = directory.resolve("Project Go - Edmund/Church project.ssmt.json");
        Files.createDirectories(java.util.Objects.requireNonNull(legacy.getParent()));
        new LocalizationProjectService().write(legacy, new LocalizationProject(1, "a16709513_wkt", "church.translation", "Church English", entries));
        byte[] legacyBefore = Files.readAllBytes(legacy);
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        assertThat(workflow.legacyProjects(directory.resolve("source"))).hasSize(1);
        var loaded = workflow.adoptLegacy(directory.resolve("source"),
                workflow.legacyProjects(directory.resolve("source")).getFirst());
        assertThat(loaded.project().entries()).hasSize(213);
        assertThat(loaded.project().entries().stream().filter(e -> !e.translatedText().isBlank())).hasSize(187);
        var restart = new TranslationWorkflow(directory.resolve("owned"));
        var resumed = restart.loadMod(directory.resolve("source"));
        Path export = directory.resolve("new-export.json");
        restart.exportTranslation(resumed, export);
        var actual = json.readTree(export.toFile()).path("entries");
        assertThat(actual.size()).isEqualTo(213);
        var originalExport = json.readTree(directory.resolve("export-207.json").toFile()).path("entries");
        var originalSources = new HashMap<String, String>();
        originalExport.forEach(item -> originalSources.put(item.path("id").asText(), item.path("source").asText()));
        assertThat(originalSources).hasSize(207);
        // Six newly covered tooltip overrides in the unchanged historical source corpus.
        originalSources.put("data/weapons/weapon_data.csv#csv:id=aDM_sele:speedStr", "普通");
        originalSources.put("data/weapons/weapon_data.csv#csv:id=aDM_sele:trackingStr", "普通");
        originalSources.put("data/weapons/weapon_data.csv#csv:id=aDM_bsnyl:speedStr", "较快");
        originalSources.put("data/weapons/weapon_data.csv#csv:id=aDM_bsnyl:trackingStr", "无");
        originalSources.put("data/weapons/weapon_data.csv#csv:id=aDM_zeluB:accuracyStr", "完美");
        originalSources.put("data/weapons/weapon_data.csv#csv:id=aDM_zeluB:customPrimary",
                "开火时向武器指向处最近的目标施放一道必定会命中的 EMP 电弧。");
        for (var item : actual) {
            String translation = expected.getOrDefault(item.path("id").asText(), "");
            assertThat(item.path("existingTranslation").asText()).isEqualTo(translation);
            assertThat(item.path("translation").asText()).isEqualTo(translation);
            assertThat(item.path("source").asText()).isEqualTo(originalSources.get(item.path("id").asText()));
        }
        assertThat(Files.readAllBytes(legacy)).isEqualTo(legacyBefore);
        assertThat(resumed.project().patchId()).isEqualTo("church.translation");
        // Complete only the new entries and exercise import/build through the same facade.
        var completedResponse = json.readTree(export.toFile());
        for (var item : completedResponse.path("entries")) {
            if (item.path("translation").asText().isBlank()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("translation", item.path("source").asText());
            }
        }
        json.writeValue(export.toFile(), completedResponse);
        var completed = restart.importTranslation(resumed, export);
        Path output = directory.resolve("translated-copy");
        restart.buildPatch(completed, output);
        assertThat(output.resolve("Project Go Changes.csv")).doesNotExist();
        assertThat(com.ssmt.patcher.PatchBuilder.sourceBackupRoot(output)).doesNotExist();
        assertThat(new TranslationWorkflow(directory.resolve("owned")).loadMod(directory.resolve("source"))
                .project().entries()).hasSize(213).allMatch(e -> !e.translatedText().isBlank());
    }
}
