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

    /** Where an author record pair was seen: the record file and the records around it. */
    private record Occurrence(String file, String previous, String next) { }

    public ObjectNode records(ObjectNode document, List<Path> paths) throws ProjectException {
        new BridgeForgeTranslationDocument().validate(document);
        Map<String, Set<String>> pairs = new HashMap<>();
        Map<String, Map<String, List<Occurrence>>> seen = new HashMap<>();
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
                        for (int index = 0; index < items.size(); index++) {
                            JsonNode item = items.get(index);
                            JsonNode target = item.has("en") ? item.path("en") : item.path("c");
                            if (item.path("zh").isTextual() && target.isTextual()) {
                                pairs.computeIfAbsent(item.path("zh").asText(), ignored -> new HashSet<>())
                                        .add(target.asText());
                                seen.computeIfAbsent(item.path("zh").asText(), ignored -> new HashMap<>())
                                        .computeIfAbsent(target.asText(), ignored -> new java.util.ArrayList<>())
                                        .add(new Occurrence(file.getFileName().toString(),
                                                index > 0 ? items.get(index - 1).path("zh").asText("") : "",
                                                index + 1 < items.size()
                                                        ? items.get(index + 1).path("zh").asText("") : ""));
                            }
                        }
                    } catch (IOException exception) {
                        // Malformed record files are not author authority.
                    }
                }
            }
            int filled = 0;
            int ambiguous = 0;
            int ranked = 0;
            var ranker = CandidateRanker.withDefaultRules();
            List<JsonNode> all = new java.util.ArrayList<>();
            document.path("entries").forEach(all::add);
            for (int position = 0; position < all.size(); position++) {
                ObjectNode entry = (ObjectNode) all.get(position);
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
                    var outcome = rankRecords(ranker, entry, all, position, seen.get(entry.path("source").asText()));
                    if (outcome != null) {
                        entry.set("candidates_ranked", outcome.ranking());
                        if (outcome.applied() != null) {
                            entry.put("translation", outcome.applied()).put("provenance", "record-ranked");
                            ranked++;
                        } else if (outcome.suggestion() != null) {
                            entry.set("suggestion", outcome.suggestion());
                        }
                    }
                }
            }
            return JSON.createObjectNode().put("filled", filled).put("ambiguous", ambiguous)
                    .put("ranked_applied", ranked);
        } catch (IOException exception) {
            throw new ProjectException("Could not discover translator records", exception);
        }
    }

    private record RankOutcome(com.fasterxml.jackson.databind.node.ArrayNode ranking, ObjectNode suggestion,
            String applied) { }

    /**
     * Ranks the competing record translations of one unit by how well the records around each pair match the units
     * around it (same neighbouring ids) and by file-name overlap; applies the leader only when the ranking rules allow.
     */
    private static RankOutcome rankRecords(CandidateRanker ranker, ObjectNode entry, List<JsonNode> all, int position,
            Map<String, List<Occurrence>> byTranslation) {
        if (byTranslation == null) {
            return null;
        }
        String file = entry.path("file").asText();
        String previous = neighbour(all, position, -1, file);
        String next = neighbour(all, position, 1, file);
        Set<String> unitTokens = tokens(file);
        List<CandidateRanker.Candidate> candidates = new java.util.ArrayList<>();
        for (var option : new java.util.TreeMap<>(byTranslation).entrySet()) {
            double neighbours = 0;
            double sameFile = 0;
            boolean measured = false;
            for (Occurrence occurrence : option.getValue()) {
                int hits = 0;
                int possible = 0;
                if (!previous.isEmpty() && !occurrence.previous().isEmpty()) {
                    possible++;
                    hits += previous.equals(occurrence.previous()) ? 1 : 0;
                }
                if (!next.isEmpty() && !occurrence.next().isEmpty()) {
                    possible++;
                    hits += next.equals(occurrence.next()) ? 1 : 0;
                }
                if (possible > 0) {
                    neighbours = Math.max(neighbours, (double) hits / possible);
                    measured = true;
                }
                Set<String> shared = new HashSet<>(tokens(occurrence.file()));
                shared.retainAll(unitTokens);
                sameFile = Math.max(sameFile, shared.isEmpty() ? 0.0 : 1.0);
            }
            Map<String, Double> features = new java.util.LinkedHashMap<>();
            features.put("same_file", sameFile);
            if (measured) {
                features.put("neighbours", neighbours);
            }
            candidates.add(new CandidateRanker.Candidate(option.getKey(), "record", features));
        }
        var ranking = ranker.rank(candidates);
        var array = JSON.createArrayNode();
        ranking.forEach(item -> array.addObject().put("text", item.text()).put("score", item.score()));
        var suggestion = ranker.suggestion(ranking);
        var automatic = ranker.autoApplicable(ranking);
        ObjectNode proposed = suggestion == null ? null
                : JSON.createObjectNode().put("text", suggestion.text()).put("score", suggestion.score());
        String applied = automatic == null ? null
                : BridgeForgePlaceholders.resolve(entry.path("source").asText(), automatic.text());
        return new RankOutcome(array, proposed, applied);
    }

    private static String neighbour(List<JsonNode> all, int position, int step, String file) {
        int index = position + step;
        return index >= 0 && index < all.size() && all.get(index).path("file").asText().equals(file)
                ? all.get(index).path("source").asText() : "";
    }

    private static Set<String> tokens(String file) {
        Set<String> result = new HashSet<>();
        String stem = file.substring(file.lastIndexOf('/') + 1);
        int dot = stem.lastIndexOf('.');
        String name = (dot > 0 ? stem.substring(0, dot) : stem).toLowerCase(java.util.Locale.ROOT);
        for (String token : name.split("[^a-z0-9]+")) {
            if (token.length() >= 3) {
                result.add(token);
            }
        }
        return result;
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
