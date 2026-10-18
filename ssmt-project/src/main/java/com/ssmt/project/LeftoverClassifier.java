package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Gives every leftover CJK item of a mod a reason, so nobody triages by hand:
 * {@code UNSUPPORTED_TYPE} (a text file kind the method does not cover), {@code UNREADABLE},
 * {@code UNTRANSLATED} (no translation supplied) and {@code REJECTED} (apply refused the supplied translation).
 * {@code PROTECTED_IDENTIFIER} is reserved for the identifier classifier (plan G2) and is not produced yet.
 */
public final class LeftoverClassifier {
    /** Reason codes, in report order. */
    public static final List<String> REASONS = List.of("UNSUPPORTED_TYPE", "UNREADABLE", "PROTECTED_IDENTIFIER",
            "UNTRANSLATED", "REJECTED");
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Plain-text suffixes a person could have written Chinese into that the shared method never reads. */
    private static final Set<String> TEXT_SUFFIXES = Set.of("txt", "md", "xml", "ini", "properties", "yml", "yaml",
            "lua", "py", "js", "html", "htm", "cfg", "conf", "log", "rtf", "tsv", "bat", "cmd", "ps1", "sh", "kt",
            "gradle", "toml", "plist");
    private static final long MAX_TEXT_BYTES = 4L * 1024L * 1024L;

    private LeftoverClassifier() { }

    /**
     * Classifies the leftovers of a mod.
     *
     * @param mod the mod directory that was checked
     * @param remaining the shared-method export of that directory (its entries are the leftover units)
     * @param document the translation document that was applied, or null when none exists (a plain check)
     * @param problems the apply problems, as {@code "<unit id>: reason"} lines
     * @return {@code {total, by_reason{}, items[]}}
     */
    public static ObjectNode classify(Path mod, JsonNode remaining, JsonNode document, List<String> problems)
            throws IOException {
        Map<String, JsonNode> supplied = new TreeMap<>();
        if (document != null) {
            for (JsonNode entry : document.path("entries")) {
                supplied.put(entry.path("id").asText(), entry);
            }
        }
        Set<String> rejected = new HashSet<>();
        for (String problem : problems) {
            int colon = problem.indexOf(": ");
            if (colon > 0) {
                rejected.add(problem.substring(0, colon));
            }
        }
        ArrayNode items = JSON.createArrayNode();
        for (JsonNode unit : remaining.path("entries")) {
            String id = unit.path("id").asText();
            JsonNode given = supplied.get(id);
            String translation = given == null ? "" : given.path("translation").asText();
            boolean refused = rejected.contains(id) || !translation.isEmpty() && !BridgeForgeTextUnits.cjk(translation);
            String detail = refused ? "translation supplied but not applied" : "no translation supplied";
            add(items, unit.path("file").asText(), id, refused ? "REJECTED" : "UNTRANSLATED", detail);
        }
        for (JsonNode diagnostic : remaining.path("unreadable")) {
            String text = diagnostic.asText();
            int cut = text.indexOf(": ");
            add(items, cut < 0 ? text : text.substring(0, cut), "", "UNREADABLE", text);
        }
        for (String file : unsupportedFiles(mod)) {
            add(items, file, "", "UNSUPPORTED_TYPE", "text file kind not covered by the translation method");
        }
        ObjectNode counts = JSON.createObjectNode();
        for (String reason : REASONS) {
            counts.put(reason, 0);
        }
        for (JsonNode item : items) {
            counts.put(item.path("reason").asText(), counts.path(item.path("reason").asText()).asInt() + 1);
        }
        ObjectNode result = JSON.createObjectNode().put("total", items.size());
        result.set("by_reason", counts);
        result.set("items", items);
        return result;
    }

    private static void add(ArrayNode items, String file, String id, String reason, String detail) {
        items.addObject().put("file", file).put("id", id).put("reason", reason).put("detail", detail);
    }

    /** Files with CJK text whose suffix the method does not read; the list is sorted and size-bounded per file. */
    static List<String> unsupportedFiles(Path mod) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(mod)) {
            files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
        List<String> result = new java.util.ArrayList<>();
        for (Path path : files) {
            String file = mod.relativize(path).toString().replace(File.separatorChar, '/');
            if (BridgeForgeTranslationService.excluded(file) || file.equals("mod_info.json")
                    || !TEXT_SUFFIXES.contains(BridgeForgeTranslationService.suffix(file))
                    || BridgeForgeTranslationService.covers(file) || Files.size(path) > MAX_TEXT_BYTES) {
                continue;
            }
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
                if (BridgeForgeTextUnits.cjk(text)) {
                    result.add(file);
                }
            } catch (CharacterCodingException ignored) {
                // Not UTF-8 text: binary or legacy; the unreadable list covers files the method tries to read.
                continue;
            }
        }
        return result;
    }
}
