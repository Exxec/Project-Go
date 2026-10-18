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
            for (String field : java.util.Set.of("file", "kind", "context", "role", "role_rule", "group")) {
                if (original.has(field)) {
                    entry.set(field, original.path(field).deepCopy());
                }
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
        linkedGroups(normalized, originals);
        if (!nativeResponse) {
            normalized.put("schemaVersion", 1).put("sourceModId", project.sourceModId())
                    .put("sourceLanguage", normalized.path("source_language").asText())
                    .put("targetLanguage", normalized.path("target_language").asText())
                    .put("originalModName", project.patchName()).put("entryCount", normalized.path("entries").size());
        }
        return normalized;
    }

    /** Linked units are translated together or not at all: one text per group, no two groups with the same text. */
    private static void linkedGroups(ObjectNode response, Map<String, JsonNode> originals) throws ProjectException {
        Map<String, java.util.List<ObjectNode>> groups = new java.util.TreeMap<>();
        for (JsonNode node : response.path("entries")) {
            String group = originals.get(node.path("id").asText()).path("group").asText("");
            if (!group.isEmpty()) {
                groups.computeIfAbsent(group, ignored -> new java.util.ArrayList<>()).add((ObjectNode) node);
            }
        }
        Map<String, String> textOwner = new HashMap<>();
        for (var group : groups.entrySet()) {
            java.util.Set<String> texts = new java.util.LinkedHashSet<>();
            group.getValue().stream().map(entry -> entry.path("translation").asText())
                    .filter(text -> !text.isEmpty()).forEach(texts::add);
            if (texts.size() > 1) {
                throw new ProjectException("Linked units " + group.getKey() + " ("
                        + group.getValue().getFirst().path("source").asText()
                        + ") must share one translation but received " + texts);
            }
            if (texts.isEmpty()) {
                continue;
            }
            String text = texts.iterator().next();
            String previous = textOwner.putIfAbsent(text, group.getKey());
            if (previous != null) {
                throw new ProjectException("Linked groups " + previous + " and " + group.getKey()
                        + " would both become '" + text + "' and collide");
            }
            group.getValue().forEach(entry -> entry.put("translation", text));
        }
    }

    private static Map<String, JsonNode> originals(ObjectNode document) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode entry : document.path("entries")) {
            result.put(entry.path("id").asText(), entry);
        }
        return result;
    }
}
