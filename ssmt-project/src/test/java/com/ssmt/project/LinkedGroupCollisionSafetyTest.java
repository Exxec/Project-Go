package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkedGroupCollisionSafetyTest {
    private static final String LINKED_KEY = "新设计";
    @TempDir Path temporary;

    private Path mod() throws Exception {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/hulls"));
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"),
                "{\"id\":\"linked-collision\",\"name\":\"Fixture\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/hulls/ship_data.csv"), "id,name,design type\nhull1,Ship," + LINKED_KEY + "\n");
        Files.writeString(mod.resolve("data/config/colors.json"),
                "{\"designTypeColors\":{\"" + LINKED_KEY + "\":\"#f00\",\"New design\":\"#0f0\"}}");
        return mod;
    }

    private ObjectNode translation(Path mod) throws Exception {
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        for (JsonNode entry : document.path("entries")) {
            if (entry.path("id").asText().equals("json:data/config/colors.json@0")) {
                ((ObjectNode) entry).put("translation", "New design");
            }
        }
        return document;
    }

    @Test void linkedJsonKeyCollisionRefusesWholeApplyBeforePublishingAnything() throws Exception {
        Path mod = mod();
        byte[] csvBefore = Files.readAllBytes(mod.resolve("data/hulls/ship_data.csv"));
        byte[] jsonBefore = Files.readAllBytes(mod.resolve("data/config/colors.json"));
        ObjectNode document = translation(mod);

        Path existingOutput = temporary.resolve("existing-output");
        Files.createDirectories(existingOutput);
        Path sentinel = existingOutput.resolve("keep.txt");
        Files.writeString(sentinel, "prior output");
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(mod, document, existingOutput, false))
                .isInstanceOf(ProjectException.class).hasMessageContaining("linked translation rejected");
        assertThat(Files.readString(sentinel)).isEqualTo("prior output");
        assertThat(Files.readAllBytes(mod.resolve("data/hulls/ship_data.csv"))).isEqualTo(csvBefore);
        assertThat(Files.readAllBytes(mod.resolve("data/config/colors.json"))).isEqualTo(jsonBefore);

        Path newOutput = temporary.resolve("new-output");
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(mod, document, newOutput, false))
                .isInstanceOf(ProjectException.class).hasMessageContaining("linked translation rejected");
        assertThat(newOutput).doesNotExist();
        assertThat(Files.readAllBytes(mod.resolve("data/hulls/ship_data.csv"))).isEqualTo(csvBefore);
        assertThat(Files.readAllBytes(mod.resolve("data/config/colors.json"))).isEqualTo(jsonBefore);
    }

    @Test void linkedJsonKeyCollisionAlsoRefusesInPlaceWithoutCreatingASnapshot() throws Exception {
        Path mod = mod();
        byte[] csvBefore = Files.readAllBytes(mod.resolve("data/hulls/ship_data.csv"));
        byte[] jsonBefore = Files.readAllBytes(mod.resolve("data/config/colors.json"));
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(mod, translation(mod), null, true))
                .isInstanceOf(ProjectException.class).hasMessageContaining("linked translation rejected");
        assertThat(Files.readAllBytes(mod.resolve("data/hulls/ship_data.csv"))).isEqualTo(csvBefore);
        assertThat(Files.readAllBytes(mod.resolve("data/config/colors.json"))).isEqualTo(jsonBefore);
        assertThat(TranslationSnapshot.list(mod)).isEmpty();
    }

    @Test void rejectedPlaceholderMemberCannotBeRefilledFromAnotherGroupMember() throws Exception {
        Path mod = mod();
        for (String file : new String[] {"data/hulls/ship_data.csv", "data/config/colors.json"}) {
            Path path = mod.resolve(file);
            Files.writeString(path, Files.readString(path).replace(LINKED_KEY, "%s" + LINKED_KEY));
        }
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        for (JsonNode entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", entry.path("kind").asText().equals("json-key")
                    ? "Missing placeholder" : "%s Safe design");
        }
        Path output = temporary.resolve("output");
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(mod, document, output, false))
                .isInstanceOf(ProjectException.class).hasMessageContaining("Linked translation rejected");
        assertThat(output).doesNotExist();
    }

    @Test void propagatedNewlineCannotPartiallyTranslateALinkedTextLine() throws Exception {
        Path mod = mod();
        Files.createDirectories(mod.resolve("data/strings"));
        Files.writeString(mod.resolve("data/strings/names.txt"), LINKED_KEY + "\n");
        ObjectNode document = translation(mod);
        for (JsonNode entry : document.path("entries")) {
            if (entry.path("kind").asText().equals("json-key")) {
                ((ObjectNode) entry).put("translation", "New\nDesign");
            }
        }
        Path output = temporary.resolve("output");
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(mod, document, output, false))
                .isInstanceOf(ProjectException.class).hasMessageContaining("linked translation rejected");
        assertThat(output).doesNotExist();
    }
}
