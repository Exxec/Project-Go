package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preflight all protected fields and all rewritten bytes before publishing any translation. */
public final class BridgeForgeTranslationApply {
    private static final ObjectMapper JSON = new ObjectMapper();
    private record Edit(int start, int end, String replacement) { }

    /** Exactly one of out or inPlace is required; in-place means an explicitly designated working copy. */
    public ObjectNode apply(Path source, ObjectNode document, Path out, boolean inPlace) throws ProjectException {
        if ((out == null) == !inPlace) {
            throw new ProjectException("Give exactly one of --out DIR or --in-place");
        }
        new BridgeForgeTranslationDocument().validate(document);
        try {
            Path root = source.toRealPath();
            var hashFields = document.path("file_hashes").fields();
            while (hashFields.hasNext()) {
                WorkflowOperation.checkCancellation();
                var field = hashFields.next();
                if (!BridgeForgeTranslationService.hash(BridgeForgeTranslationService.contained(root, field.getKey()))
                        .equalsIgnoreCase(field.getValue().asText())) {
                    throw new IOException("Source files changed since export: " + field.getKey());
                }
            }
            ObjectNode current = new BridgeForgeTranslationService().export(root);
            if (!current.path("mod_id").equals(document.path("mod_id"))) {
                throw new IOException("Translation mod_id differs from selected source");
            }
            Map<String, JsonNode> discovered = new HashMap<>();
            for (JsonNode entry : current.path("entries")) {
                discovered.put(entry.path("id").asText(), entry);
            }
            Map<String, Map<String, String>> byFile = new LinkedHashMap<>();
            var problems = JSON.createArrayNode();
            var reindexed = JSON.createArrayNode();
            for (JsonNode entry : document.path("entries")) {
                WorkflowOperation.checkCancellation();
                String id = entry.path("id").asText();
                JsonNode actual = discovered.get(id);
                if (actual == null) {
                    throw new IOException("Translation identity is absent in source: " + id);
                }
                for (String field : Set.of("file", "kind", "context", "source")) {
                    if (!actual.path(field).equals(entry.path(field))) {
                        throw new IOException("Protected entry field differs: " + id + " " + field);
                    }
                }
                String target = entry.path("translation").asText();
                if (target.isEmpty()) {
                    target = document.path("glossary").path(entry.path("source").asText()).asText("");
                }
                if (target.isEmpty()) {
                    continue;
                }
                String resolved = BridgeForgePlaceholders.resolve(entry.path("source").asText(), target);
                if (resolved == null) {
                    problems.add(id + ": placeholders differ");
                    continue;
                }
                if (!resolved.equals(target)) {
                    reindexed.add(id);
                }
                byFile.computeIfAbsent(entry.path("file").asText(), ignored -> new LinkedHashMap<>()).put(id, resolved);
            }
            Map<Path, byte[]> originals = new LinkedHashMap<>();
            Map<Path, byte[]> rewritten = new LinkedHashMap<>();
            ObjectNode applied = JSON.createObjectNode();
            for (var file : byFile.entrySet()) {
                Path path = BridgeForgeTranslationService.contained(root, file.getKey());
                byte[] original = Files.readAllBytes(path);
                byte[] replacement;
                if (BridgeForgeTranslationService.suffix(file.getKey()).equals("jar")) {
                    Map<String, Map<Integer, String>> classes = new HashMap<>();
                    for (String id : file.getValue().keySet()) {
                        var entry = discovered.get(id);
                        String name = entry.path("context").path("class").asText() + ".class";
                        classes.computeIfAbsent(name, ignored -> new HashMap<>()).put(
                                entry.path("context").path("cp_index").asInt(), file.getValue().get(id));
                    }
                    replacement = BridgeForgeJarRewrite.rewrite(original, classes);
                    applied.put("jar", applied.path("jar").asInt() + file.getValue().size());
                } else {
                    String text = BridgeForgeTranslationService.readText(path);
                    var units = BridgeForgeTranslationService.units(file.getKey(), text);
                    List<Edit> edits = new ArrayList<>();
                    Set<String> siblingKeys = new HashSet<>();
                    for (var unit : units) {
                        if (unit.kind().equals("json-key")) {
                            String sibling = parent(unit.context().path("path")) + "\0" + unit.source();
                            siblingKeys.add(sibling);
                        }
                    }
                    for (var unit : units) {
                        String target = file.getValue().get(unit.id());
                        // Non-CJK JSON literals are retained only to check sibling key collisions.
                        if (target == null || !BridgeForgeTextUnits.cjk(unit.source())) {
                            continue;
                        }
                        if (unit.kind().equals("json-key")) {
                            String sibling = parent(unit.context().path("path")) + "\0" + target;
                            if (!siblingKeys.add(sibling)) {
                                problems.add(unit.id() + ": translated key already exists in the same object");
                                continue;
                            }
                        }
                        String token = unit.kind().equals("csv") ? csvQuote(target, unit.quoted())
                                : unit.kind().equals("java") ? javaQuote(target) : JSON.writeValueAsString(target);
                        edits.add(new Edit(unit.start(), unit.end(), token));
                    }
                    StringBuilder changed = new StringBuilder(text);
                    edits.sort(java.util.Comparator.comparingInt(Edit::start).reversed());
                    for (Edit edit : edits) {
                        changed.replace(edit.start(), edit.end(), edit.replacement());
                    }
                    String result = changed.toString();
                    String kind = BridgeForgeTranslationService.suffix(file.getKey());
                    if (kind.equals("csv") && !shape(text).equals(shape(result))) {
                        throw new IOException("CSV row shape would change: " + file.getKey());
                    }
                    if (!kind.equals("csv") && !kind.equals("java")) {
                        // Token replacement must preserve the parse structure and leaf/key count.
                        if (!structure(text).equals(structure(result))
                                || BridgeForgeTextUnits.json(file.getKey(), text).size()
                                != BridgeForgeTextUnits.json(file.getKey(), result).size()) {
                            throw new IOException("JSON-like structure would change: " + file.getKey());
                        }
                    }
                    boolean bom = original.length >= 3 && original[0] == (byte) 0xef
                            && original[1] == (byte) 0xbb && original[2] == (byte) 0xbf;
                    var charset = BridgeForgeTranslationService.encoding(path, original);
                    var encoded = charset.newEncoder().encode(java.nio.CharBuffer.wrap((bom ? "\ufeff" : "") + result));
                    replacement = new byte[encoded.remaining()];
                    encoded.get(replacement);
                    String counter = kind.equals("csv") ? "csv" : kind.equals("java") ? "java" : "json";
                    applied.put(counter, applied.path(counter).asInt() + edits.size());
                }
                originals.put(path, original);
                rewritten.put(path, replacement);
            }
            Path target = inPlace ? root : out.toAbsolutePath().normalize();
            if (!inPlace) {
                if (Files.exists(target) || target.startsWith(root) || root.startsWith(target)) {
                    throw new IOException("Output must be a new directory outside the source");
                }
                Path parentPath = target.getParent();
                if (parentPath == null) {
                    throw new IOException("Output has no parent directory");
                }
                Path parent = parentPath.toRealPath();
                target = parent.resolve(target.getFileName());
                if (target.startsWith(root)) {
                    throw new IOException("Output resolves inside the source");
                }
                Path staging = Files.createTempDirectory(parent, ".p7-translation-");
                try {
                    copy(root, staging);
                    for (var entry : rewritten.entrySet()) {
                        Path destination = staging.resolve(root.relativize(entry.getKey()));
                        destination.toFile().setWritable(true);
                        Files.write(destination, entry.getValue());
                    }
                    verifySources(originals, document, root);
                    Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException exception) {
                    remove(staging);
                    throw exception;
                }
            } else {
                verifySources(originals, document, root);
                List<Path> changed = new ArrayList<>();
                try {
                    for (var entry : rewritten.entrySet()) {
                        changed.add(entry.getKey());
                        Files.write(entry.getKey(), entry.getValue());
                    }
                } catch (IOException exception) {
                    for (Path path : changed.reversed()) {
                        try {
                            Files.write(path, originals.get(path));
                        } catch (IOException rollback) {
                            exception.addSuppressed(rollback);
                        }
                    }
                    throw exception;
                }
            }
            var remaining = new BridgeForgeTranslationService().export(target);
            int leftover = remaining.path("entry_count").asInt();
            for (JsonNode diagnostic : remaining.path("unreadable")) {
                problems.add("Unreadable leftover-check input: " + diagnostic.asText());
            }
            ObjectNode report = JSON.createObjectNode().put("schema_version", 1).put("mode", "TRANSLATION_APPLY")
                    .put("target", target.toString()).put("entries", document.path("entries").size())
                    .put("leftover_cjk_units", leftover);
            report.set("applied", applied);
            report.set("problems", problems);
            report.set("format_slots_reindexed", reindexed);
            report.put("status", problems.isEmpty() && leftover == 0 ? "OK" : rewritten.isEmpty() ? "FAIL" : "PARTIAL");
            return report;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not apply BridgeForge translation: " + exception.getMessage(), exception);
        }
    }

    private static void verifySources(Map<Path, byte[]> originals, ObjectNode document, Path root) throws IOException {
        for (var original : originals.entrySet()) {
            if (!java.util.Arrays.equals(original.getValue(), Files.readAllBytes(original.getKey()))) {
                throw new IOException("Source changed during translation preflight");
            }
        }
        var hashes = document.path("file_hashes").fields();
        while (hashes.hasNext()) {
            var hash = hashes.next();
            if (!BridgeForgeTranslationService.hash(BridgeForgeTranslationService.contained(root, hash.getKey()))
                    .equalsIgnoreCase(hash.getValue().asText())) {
                throw new IOException("Source hash changed during translation preflight");
            }
        }
    }

    private static String parent(JsonNode path) {
        var copy = path.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) copy).remove(copy.size() - 1);
        return copy.toString();
    }

    private static String csvQuote(String value, boolean quoted) {
        return quoted || value.contains(",") || value.contains("\"") || value.contains("\r")
                || value.contains("\n") || !value.equals(value.strip())
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static String javaQuote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            result.append(switch (ch) {
                case '\\' -> "\\\\";
                case '"' -> "\\\"";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                case '\b' -> "\\b";
                case '\f' -> "\\f";
                default -> ch < 32 ? String.format("\\u%04x", (int) ch) : String.valueOf(ch);
            });
        }
        return result.append('"').toString();
    }

    private static List<Integer> shape(String text) {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        for (var cell : BridgeForgeTextUnits.cells(text)) {
            rows.merge(cell.row(), 1, Integer::sum);
        }
        return List.copyOf(rows.values());
    }

    private static String structure(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '"' || ch == '\'') {
                char quote = ch;
                while (++index < text.length()) {
                    ch = text.charAt(index);
                    if (ch == '\\') {
                        index++;
                    } else if (ch == quote) {
                        break;
                    }
                }
            } else if (ch == '#' || text.startsWith("//", index)) {
                while (index < text.length() && text.charAt(index) != '\n') {
                    index++;
                }
            } else if ("{}[]:,".indexOf(ch) >= 0) {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static void copy(Path source, Path destination) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Source contains linked path " + path);
                }
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void remove(Path staging) throws IOException {
        try (var walk = Files.walk(staging)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
