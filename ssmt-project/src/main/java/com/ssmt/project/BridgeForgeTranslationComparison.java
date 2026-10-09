package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Read-only discovery comparison against a source-bound BridgeForge export. */
public final class BridgeForgeTranslationComparison {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Compares protected units, ignoring translations, glossary and entry ordering. */
    public ObjectNode compare(Path source, ObjectNode reference) throws ProjectException {
        new BridgeForgeTranslationDocument().validate(reference);
        ObjectNode current = new BridgeForgeTranslationService().export(source);
        ObjectNode report = JSON.createObjectNode().put("schema_version", 1)
                .put("mode", "TRANSLATION_COMPARISON").put("mod_id", current.path("mod_id").asText());
        report.put("scope", "Discovered CJK units and reference-hashed files; not complete mod coverage");
        report.set("project_go_file_hashes", current.path("file_hashes").deepCopy());
        report.set("reference_file_hashes", reference.path("file_hashes").deepCopy());
        var mismatches = report.putArray("source_mismatches");
        if (!current.path("mod_id").equals(reference.path("mod_id"))) {
            mismatches.add("mod_id");
        }
        try {
            Path root = source.toRealPath();
            var hashes = reference.path("file_hashes").fields();
            while (hashes.hasNext()) {
                var hash = hashes.next();
                Path file = BridgeForgeTranslationService.contained(root, hash.getKey());
                if (!Files.isRegularFile(file)
                        || !BridgeForgeTranslationService.hash(file).equalsIgnoreCase(hash.getValue().asText())) {
                    mismatches.add(hash.getKey());
                }
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not verify comparison source: " + exception.getMessage(), exception);
        }
        report.set("project_go_unreadable", current.path("unreadable").deepCopy());
        report.set("reference_unreadable", reference.path("unreadable").deepCopy());
        Map<String, JsonNode> actual = index(current);
        Map<String, JsonNode> expected = index(reference);
        var ids = new TreeSet<>(actual.keySet());
        ids.addAll(expected.keySet());
        var differences = report.putArray("differences");
        for (String id : ids) {
            JsonNode left = actual.get(id);
            JsonNode right = expected.get(id);
            if (left == null || right == null) {
                JsonNode unit = left == null ? right : left;
                differences.addObject().put("id", id).put("file", unit.path("file").asText())
                        .put("reason", left == null ? "MISSING_IN_PROJECT_GO" : "EXTRA_IN_PROJECT_GO");
            } else {
                var changed = JSON.createArrayNode();
                for (String field : java.util.List.of("file", "kind", "context", "source")) {
                    if (!left.path(field).equals(right.path(field))) {
                        changed.add(field);
                    }
                }
                if (!changed.isEmpty()) {
                    var difference = differences.addObject().put("id", id)
                            .put("file", left.path("file").asText()).put("reason", "PROTECTED_UNIT_CHANGED");
                    difference.set("fields", changed);
                    difference.put("reference_file", right.path("file").asText());
                }
            }
        }
        report.put("project_go_units", actual.size()).put("reference_units", expected.size());
        String status = !mismatches.isEmpty() ? "SOURCE_MISMATCH"
                : !current.path("unreadable").isEmpty() || !reference.path("unreadable").isEmpty() ? "INCOMPLETE"
                : differences.isEmpty() ? "MATCH" : "DIFFERENCES";
        return report.put("status", status);
    }

    /** Persists a new report outside the read-only mod; never overwrites existing evidence. */
    public void write(Path source, Path destination, ObjectNode report) throws ProjectException {
        try {
            Path target = destination.toAbsolutePath().normalize();
            Path targetParent = target.getParent();
            if (targetParent == null) {
                throw new IllegalArgumentException("Comparison report needs a file path");
            }
            Path parent = targetParent.toRealPath();
            if (parent.resolve(target.getFileName()).startsWith(source.toRealPath())) {
                throw new IllegalArgumentException("Comparison report must be outside the source mod");
            }
            Files.write(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not write comparison report: " + exception.getMessage(), exception);
        }
    }

    private static Map<String, JsonNode> index(ObjectNode document) {
        Map<String, JsonNode> result = new TreeMap<>();
        for (JsonNode entry : document.path("entries")) {
            result.put(entry.path("id").asText(), entry);
        }
        return result;
    }
}
