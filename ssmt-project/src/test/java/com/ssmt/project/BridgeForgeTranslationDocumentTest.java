package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeForgeTranslationDocumentTest {
    @TempDir Path temporary;
    private final BridgeForgeTranslationDocument service = new BridgeForgeTranslationDocument();

    private static ObjectNode fixture() throws Exception {
        return (ObjectNode) new ObjectMapper().readTree("""
                {"schema_version":1,"mode":"TRANSLATION_EXPORT","mod_id":"fixture",
                 "source_language":"zh","target_language":"en","instructions":"Keep markers",
                 "file_hashes":{"data/a.csv":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                 "unreadable":[],"glossary":{"原文":"Text"},
                 "entries":[{"id":"csv:data/a.csv#x:name","file":"data/a.csv","kind":"csv",
                  "context":{"row":"x","column":"name"},"source":"原文","translation":"Text",
                  "provenance":"record"}],"extension":{"retained":true}}
                """);
    }

    @Test void roundTripsAllFieldsAndRefusesOverwrite() throws Exception {
        ObjectNode document = fixture();
        Path file = temporary.resolve("document.json");
        service.write(file, document);
        assertThat(service.read(file)).isEqualTo(document);
        byte[] before = Files.readAllBytes(file);
        assertThatThrownBy(() -> service.write(file, document)).isInstanceOf(ProjectException.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }

    @Test void rejectsMissingHashesDuplicatesAndUnsafePaths() throws Exception {
        ObjectNode document = fixture();
        ((ObjectNode) document.path("file_hashes")).removeAll();
        assertThatThrownBy(() -> service.validate(document)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode duplicate = fixture();
        com.fasterxml.jackson.databind.JsonNode item = duplicate.path("entries").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.path("entries")).add(item);
        assertThatThrownBy(() -> service.validate(duplicate)).isInstanceOf(IllegalArgumentException.class);
        for (String path : java.util.List.of("../escape", "C:/escape", "/escape", "a/../escape", "a\\escape")) {
            assertThatThrownBy(() -> BridgeForgeTranslationDocument.safePath(path))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsDuplicateJsonFieldsAndWrongSchema() throws Exception {
        Path file = temporary.resolve("forged.json");
        Files.writeString(file, "{\"schema_version\":1,\"schema_version\":2}");
        assertThatThrownBy(() -> service.read(file)).isInstanceOf(ProjectException.class);
        ObjectNode document = fixture();
        document.put("schema_version", 2);
        assertThatThrownBy(() -> service.validate(document)).isInstanceOf(IllegalArgumentException.class);
    }
}
