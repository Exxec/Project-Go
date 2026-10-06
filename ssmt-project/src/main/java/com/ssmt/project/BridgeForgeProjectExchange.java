package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;

/** Adds schema-v1 interoperability while retaining GUI/Auto response identity and review contracts. */
final class BridgeForgeProjectExchange {
    static void exportFields(ObjectNode root, LocalizationProject project) throws ProjectException {
        ObjectNode snapshot = project.methodDocument();
        if (snapshot == null) {
            return;
        }
        for (String field : java.util.Set.of("schema_version", "mode", "mod_id", "source_language",
                "target_language", "file_hashes", "unreadable", "glossary")) {
            root.set(field, snapshot.path(field).deepCopy());
        }
        Map<String, JsonNode> originals = originals(snapshot);
        for (JsonNode node : root.path("entries")) {
            ObjectNode entry = (ObjectNode) node;
            JsonNode original = originals.get(entry.path("id").asText());
            if (original == null) {
                throw new ProjectException("Export contains an unknown method identity");
            }
            for (String field : java.util.Set.of("file", "kind", "context")) {
                entry.set(field, original.path(field).deepCopy());
            }
        }
        root.put("entry_count", root.path("entries").size());
        new BridgeForgeTranslationDocument().validate(root);
    }

    static JsonNode importFields(JsonNode root, LocalizationProject project) throws ProjectException {
        ObjectNode snapshot = project.methodDocument();
        if (snapshot == null || !root.has("schema_version")) {
            return root;
        }
        new BridgeForgeTranslationDocument().validate(root);
        for (String field : java.util.Set.of("mod_id", "source_language", "target_language", "file_hashes")) {
            if (!root.path(field).equals(snapshot.path(field))) {
                throw new ProjectException("BridgeForge response changed protected " + field);
            }
        }
        boolean nativeResponse = root.has("schemaVersion");
        ObjectNode normalized = ((ObjectNode) root).deepCopy();
        Map<String, JsonNode> originals = originals(snapshot);
        for (JsonNode node : normalized.path("entries")) {
            ObjectNode entry = (ObjectNode) node;
            JsonNode original = originals.get(entry.path("id").asText());
            if (original == null) {
                throw new ProjectException("Unknown BridgeForge response identity");
            }
            for (String field : java.util.Set.of("file", "kind", "context", "source")) {
                if (!entry.path(field).equals(original.path(field))) {
                    throw new ProjectException("BridgeForge response changed protected entry " + field);
                }
            }
            if (entry.path("translation").asText().isEmpty()) {
                entry.put("translation", normalized.path("glossary").path(entry.path("source").asText()).asText(""));
            }
            if (!nativeResponse) {
                entry.remove("provenance");
            }
        }
        if (!nativeResponse) {
            normalized.put("schemaVersion", 1).put("sourceModId", project.sourceModId())
                    .put("sourceLanguage", normalized.path("source_language").asText())
                    .put("targetLanguage", normalized.path("target_language").asText())
                    .put("originalModName", project.patchName()).put("entryCount", normalized.path("entries").size());
        }
        return normalized;
    }

    private static Map<String, JsonNode> originals(ObjectNode document) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode entry : document.path("entries")) {
            result.put(entry.path("id").asText(), entry);
        }
        return result;
    }
}
