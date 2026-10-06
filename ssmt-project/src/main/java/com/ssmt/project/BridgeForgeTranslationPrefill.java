package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Author-record and same-layout English-reference drafts; existing translations stay intact. */
public final class BridgeForgeTranslationPrefill {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ObjectNode records(ObjectNode document, List<Path> paths) throws ProjectException {
        new BridgeForgeTranslationDocument().validate(document);
        Map<String, Set<String>> pairs = new HashMap<>();
        try {
            for (Path path : paths) {
                List<Path> files;
                if (Files.isDirectory(path)) {
                    try (var walk = Files.walk(path)) {
                        files = walk.filter(file -> Files.isRegularFile(file)
                                && file.toString().endsWith(".json")).sorted().toList();
                    }
                } else {
                    files = List.of(path);
                }
                for (Path file : files) {
                    try {
                        if (Files.size(file) > 64L * 1024L * 1024L) {
                            continue;
                        }
                        JsonNode items = JSON.readTree(file.toFile());
                        if (items == null || !items.isArray()) {
                            continue;
                        }
                        for (JsonNode item : items) {
                            JsonNode target = item.has("en") ? item.path("en") : item.path("c");
                            if (item.path("zh").isTextual() && target.isTextual()) {
                                pairs.computeIfAbsent(item.path("zh").asText(), ignored -> new HashSet<>())
                                        .add(target.asText());
                            }
                        }
                    } catch (IOException exception) {
                        // Malformed record files are not author authority.
                    }
                }
            }
            int filled = 0;
            int ambiguous = 0;
            for (JsonNode node : document.path("entries")) {
                ObjectNode entry = (ObjectNode) node;
                if (!entry.path("translation").asText().isEmpty()) {
                    continue;
                }
                Set<String> options = pairs.getOrDefault(entry.path("source").asText(), Set.of());
                if (options.size() == 1) {
                    entry.put("translation", options.iterator().next()).put("provenance", "record");
                    filled++;
                } else if (!options.isEmpty()) {
                    entry.set("candidates", JSON.valueToTree(options.stream().sorted().toList()));
                    ambiguous++;
                }
            }
            return JSON.createObjectNode().put("filled", filled).put("ambiguous", ambiguous);
        } catch (IOException exception) {
            throw new ProjectException("Could not discover translator records", exception);
        }
    }

    public ObjectNode reference(ObjectNode document, Path source, Path reference) throws ProjectException {
        new BridgeForgeTranslationDocument().validate(document);
        Map<String, Set<String>> learned = new HashMap<>();
        Map<String, Map<String, String>> csv = new HashMap<>();
        Map<String, Map<JsonNode, String>> json = new HashMap<>();
        Map<String, Map<Integer, String>> jars = new HashMap<>();
        int filled = 0;
        try {
            Path ownRoot = source.toRealPath();
            Path referenceRoot = reference.toRealPath();
            for (JsonNode node : document.path("entries")) {
                ObjectNode entry = (ObjectNode) node;
                if (!entry.path("translation").asText().isEmpty()) {
                    continue;
                }
                String file = entry.path("file").asText();
                Path path = BridgeForgeTranslationService.contained(referenceRoot, file);
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String english = null;
                switch (entry.path("kind").asText()) {
                    case "csv" -> {
                        if (!csv.containsKey(file)) {
                            // All cells are needed here, including English reference cells.
                            String text = BridgeForgeTranslationService.readText(path);
                            csv.put(file, referenceCsv(file, text));
                        }
                        english = csv.get(file).get(entry.path("id").asText());
                    }
                    case "json" -> {
                        if (!json.containsKey(file)) {
                            Map<JsonNode, String> table = new HashMap<>();
                            for (var unit : BridgeForgeTextUnits.json(file, BridgeForgeTranslationService.readText(path))) {
                                if (unit.kind().equals("json")) {
                                    table.put(unit.context().path("path"), unit.source());
                                }
                            }
                            json.put(file, table);
                        }
                        english = json.get(file).get(entry.path("context").path("path"));
                    }
                    case "jar" -> {
                        String member = entry.path("context").path("class").asText() + ".class";
                        String key = file + "!" + member;
                        if (!jars.containsKey(key)) {
                            jars.put(key, aligned(BridgeForgeTranslationService.contained(ownRoot, file), path, member));
                        }
                        english = jars.get(key).get(entry.path("context").path("cp_index").asInt());
                    }
                    default -> { /* reference prefill excludes Java and JSON keys */ }
                }
                if (english == null || english.isBlank() || BridgeForgeTextUnits.cjk(english)) {
                    continue;
                }
                if (!BridgeForgePlaceholders.tokens(entry.path("source").asText())
                        .equals(BridgeForgePlaceholders.tokens(english))) {
                    entry.put("reference_hint", english);
                    continue;
                }
                entry.put("translation", english).put("provenance", "reference");
                learned.computeIfAbsent(entry.path("source").asText(), ignored -> new HashSet<>()).add(english);
                filled++;
            }
            int spread = 0;
            for (JsonNode node : document.path("entries")) {
                ObjectNode entry = (ObjectNode) node;
                Set<String> options = learned.getOrDefault(entry.path("source").asText(), Set.of());
                if (entry.path("translation").asText().isEmpty() && options.size() == 1) {
                    entry.put("translation", options.iterator().next()).put("provenance", "reference-same-text");
                    spread++;
                }
            }
            return JSON.createObjectNode().put("filled", filled).put("spread", spread);
        } catch (IOException exception) {
            throw new ProjectException("Could not prefill English reference", exception);
        }
    }

    private static Map<String, String> referenceCsv(String file, String text) {
        // Derive keys from raw rows so English reference contents never affect identity.
        List<BridgeForgeTextUnits.Cell> cells = BridgeForgeTextUnits.cells(text);
        var header = cells.stream().filter(cell -> cell.row() == 0).map(cell -> cell.value().strip()).toList();
        int idColumn = Math.max(0, header.indexOf("id"));
        int typeColumn = header.indexOf("type");
        Map<Integer, Map<Integer, String>> rows = new java.util.LinkedHashMap<>();
        for (var cell : cells) {
            rows.computeIfAbsent(cell.row(), ignored -> new HashMap<>()).put(cell.column(), cell.value());
        }
        Map<String, Integer> seen = new HashMap<>();
        Map<String, String> table = new HashMap<>();
        for (var row : rows.entrySet()) {
            if (row.getKey() == 0) {
                continue;
            }
            String key = row.getValue().getOrDefault(idColumn, "").strip();
            if (key.isEmpty()) {
                key = "row" + row.getKey();
            }
            String type = row.getValue().getOrDefault(typeColumn, "").strip();
            if (!type.isEmpty()) {
                key += "/" + type;
            }
            int occurrence = seen.merge(key, 1, Integer::sum);
            key += occurrence == 1 ? "" : "~" + occurrence;
            for (var cell : row.getValue().entrySet()) {
                if (cell.getKey() < header.size()) {
                    table.put("csv:" + file + "#" + key + ":" + header.get(cell.getKey()), cell.getValue());
                }
            }
        }
        return table;
    }

    private static Map<Integer, String> aligned(Path own, Path reference, String member) {
        try (var ownJar = new java.util.zip.ZipFile(own.toFile());
                var referenceJar = new java.util.zip.ZipFile(reference.toFile())) {
            var ownEntry = ownJar.getEntry(member);
            var referenceEntry = referenceJar.getEntry(member);
            if (ownEntry == null || referenceEntry == null) {
                return Map.of();
            }
            try (var ownInput = ownJar.getInputStream(ownEntry);
                    var referenceInput = referenceJar.getInputStream(referenceEntry)) {
                var original = BridgeForgeClassFile.parse(ownInput.readNBytes(64 * 1024 * 1024));
                var other = BridgeForgeClassFile.parse(referenceInput.readNBytes(64 * 1024 * 1024));
                var anchors = original.utf8().entrySet().stream()
                        .filter(entry -> !BridgeForgeTextUnits.cjk(entry.getValue())).toList();
                long matches = anchors.stream().filter(entry -> entry.getValue().equals(other.utf8().get(entry.getKey()))).count();
                return original.count() == other.count() && !anchors.isEmpty()
                        && (double) matches / anchors.size() >= 0.9 ? other.utf8() : Map.of();
            }
        } catch (IOException exception) {
            return Map.of();
        }
    }
}
