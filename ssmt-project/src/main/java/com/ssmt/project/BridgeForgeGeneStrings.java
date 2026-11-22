package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.BytecodeTextAllowlist;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.extractor.bytecode.ClassStringExtractor;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import com.ssmt.extractor.text.MissionTextExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Project Go P9 item 1 (BridgeForge 94.G2): a BridgeForge gene's player-visible strings as one unit. The export is a filter
 * over the units Project Go already extracts (standard CSV, JSON-like, mission text and bytecode extractors), so each
 * string keeps its stable key; nothing is re-extracted differently. A gene member selects units:
 * <ul>
 *   <li>{@code file}: every unit of that file;</li>
 *   <li>{@code csv-row} ({@code <table> [<id>]}): the units of the rows whose first identity value is the id, when the
 *       table's first identity column is BridgeForge's key column ({@code id}; {@code faction} for factions.csv); otherwise
 *       the member is listed as not mappable;</li>
 *   <li>{@code json-entry} ({@code <file>#<pointer>}): the units at or under the pointer;</li>
 *   <li>{@code jar-class} ({@code <jar>!<class entry>}): that class's bytecode units in the jar.</li>
 * </ul>
 * A unit in an id-keyed CSV row named by one of the gene's own ids that no member selects is reported as {@code uncovered}:
 * a player-visible string the gene's closure in BridgeForge may leave out. A member whose path is malformed, or whose
 * table Project Go keys by another column, is {@code not_mappable}; a row, entry or class member that selects no unit is
 * listed in {@code no_units}. The result carries BridgeForge's evidence header (WP5) about the first gene; for a set its
 * inputs are every exported gene's member files, as BridgeForge's own set proof lists them. Every gene's licence and
 * provenance travel with its strings (GD-2). Read only; the mod is never written.
 */
public final class BridgeForgeGeneStrings {
    /** The export's format name. */
    public static final String FORMAT = "project-go-gene-strings";
    /** The export's format version. */
    public static final int FORMAT_VERSION = 1;
    /** The evidence kind written in the header. */
    public static final String EVIDENCE_KIND = "gene-strings";
    /**
     * The key column of every table Project Go extracts that a BridgeForge csv-row can name. BridgeForge keys factions.csv
     * by {@code faction}, but no standard extractor reads that table; a table keyed by another column is not mappable.
     */
    static final String KEY_COLUMN = "id";
    private static final ObjectMapper JSON = new ObjectMapper();

    private BridgeForgeGeneStrings() { }

    /**
     * The export for {@code genes} (the first is the one the header is about) over the mod at {@code modRoot}.
     *
     * @param genes gene manifests, each valid under the pinned contract
     * @param modRoot the mod folder the genes were described from (read only)
     * @param producedBy Project Go's version
     * @return the export document
     * @throws ProjectException when a manifest is invalid or the mod cannot be read
     */
    public static ObjectNode export(List<JsonNode> genes, Path modRoot, String producedBy) throws ProjectException {
        if (genes.isEmpty()) {
            throw new ProjectException("No gene manifest given");
        }
        for (JsonNode gene : genes) {
            List<String> problems = BridgeForgeGeneManifest.validate(gene);
            if (!problems.isEmpty()) {
                throw new ProjectException("Not a gene manifest under the pinned contract: " + String.join("; ", problems));
            }
        }
        RootStringDiffService.Extracted extracted = RootStringDiffService.extract(modRoot, List.of(new StandardCsvFileExtractor(),
                new StandardJsonFileExtractor(), new MissionTextExtractor(), new ClassStringExtractor()));
        Map<String, List<ExtractedString>> byFile = new TreeMap<>();
        for (ExtractedString string : extracted.strings()) {
            byFile.computeIfAbsent(posix(string.sourceFile()), file -> new ArrayList<>()).add(string);
        }
        Set<String> inputs = new TreeSet<>();
        Set<String> unreadable = new TreeSet<>();
        ArrayNode geneEntries = JSON.createArrayNode();
        for (JsonNode gene : genes) {
            geneEntries.add(geneEntry(gene, byFile, extracted.unreadable(), inputs, unreadable));
        }
        JsonNode first = genes.getFirst();
        ObjectNode document = JSON.createObjectNode();
        document.put("evidence", BridgeForgeEvidenceHeader.EVIDENCE);
        document.put("schema_version", BridgeForgeEvidenceHeader.SCHEMA_VERSION);
        document.put("kind", EVIDENCE_KIND);
        ObjectNode about = document.putObject("gene");
        about.put("identity", first.get("identity").asText());
        about.set("schema_version", first.get("schema_version"));
        ObjectNode produced = document.putObject("produced_by");
        produced.put("tool", "project-go");
        produced.put("version", producedBy);
        document.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString());
        document.putObject("build").set("game_version", first.path("build").path("game_version").isTextual()
                ? first.get("build").get("game_version") : JSON.nullNode());
        ArrayNode rows = document.putArray("inputs");
        Set<String> missing = new TreeSet<>();
        for (String path : inputs) {
            Path file = modRoot.resolve(path);
            if (!Files.isRegularFile(file)) {
                missing.add(path);
                continue;
            }
            String digest;
            try {
                digest = sha256(file);
            } catch (ProjectException exception) {
                unreadable.add(path);  // a member file that exists but cannot be read fails the export, not aborts it
                continue;
            }
            ObjectNode row = rows.addObject();
            row.put("role", "member");
            row.put("path", path);
            row.put("sha256", digest);
        }
        document.put("verdict", unreadable.isEmpty() && missing.isEmpty() ? "PASS" : "FAIL");
        document.put("format", FORMAT);
        document.put("format_version", FORMAT_VERSION);
        document.set("unreadable_member_files", JSON.valueToTree(List.copyOf(unreadable)));
        document.set("missing_member_files", JSON.valueToTree(List.copyOf(missing)));
        // BYTECODE_ALLOWLIST.md: without a reviewed catalog, bytecode extraction returns raw string constants, which is
        // "not a semantic determination" that they are player-visible. Say so rather than claim it.
        document.put("bytecode_catalog", Files.isRegularFile(modRoot.resolve(BytecodeTextAllowlist.FILE_NAME)) ? "present"
                : "absent: jar-class units are raw string constants, not reviewed as player-visible");
        document.set("genes", geneEntries);
        List<String> problems = BridgeForgeEvidenceHeader.validate(document);
        if (!problems.isEmpty()) {
            throw new ProjectException("The export's evidence header is malformed: " + String.join("; ", problems));
        }
        return document;
    }

    private static ObjectNode geneEntry(JsonNode gene, Map<String, List<ExtractedString>> byFile, Set<String> unreadableFiles,
            Set<String> inputs, Set<String> unreadable) {
        Map<String, ObjectNode> units = new LinkedHashMap<>();
        ArrayNode notMappable = JSON.createArrayNode();
        ArrayNode noUnits = JSON.createArrayNode();
        for (JsonNode member : gene.get("members")) {
            String what = member.get("what").asText();
            String path = member.get("path").asText();
            if (!wellFormed(what, path)) {
                notMappable.add(path);  // review 2026-11-22: a malformed path is reported, never a stack trace
                continue;
            }
            String file = memberFile(what, path);
            inputs.add(file);
            if (unreadableFiles.contains(file)) {
                unreadable.add(file);
            }
            List<ExtractedString> candidates = byFile.getOrDefault(file, List.of());
            if ("csv-row".equals(what) && !candidates.isEmpty()
                    && (!keyedBy(candidates.getFirst().key(), KEY_COLUMN) || rowIdentities(path, candidates) > 1)) {
                notMappable.add(path);
                continue;
            }
            boolean selected = false;  // review 2026-11-22: counted per member, so overlapping members are not "no units"
            for (ExtractedString string : candidates) {
                if (selects(what, path, string.key())) {
                    selected = true;
                    units.computeIfAbsent(file + "\0" + string.key(), ignored -> unit(path, file, string));
                }
            }
            if (!selected && !"file".equals(what)) {
                noUnits.add(path);  // a row, entry or class that holds no text Project Go extracts (factions.csv, say)
            }
        }
        ObjectNode entry = JSON.createObjectNode();
        entry.put("anchor", gene.get("anchors").get(0).asText());
        entry.put("identity", gene.get("identity").asText());
        entry.set("licence", gene.get("licence"));
        entry.set("provenance", gene.get("provenance"));
        entry.set("units", JSON.valueToTree(List.copyOf(units.values())));
        entry.set("uncovered", uncovered(gene, byFile, units.keySet()));
        entry.set("not_mappable", notMappable);
        entry.set("no_units", noUnits);
        return entry;
    }

    /**
     * How many rows a csv-row member's id matches. More than one happens in a table keyed by id and type (descriptions.csv
     * with a SHIP and a CUSTOM row of one id): BridgeForge's member names the first such row only, so it is not mappable.
     */
    private static int rowIdentities(String path, List<ExtractedString> candidates) {
        Set<String> identities = new HashSet<>();
        for (ExtractedString string : candidates) {
            String key = string.key();
            if (selects("csv-row", path, key)) {
                int equals = key.indexOf('=');
                identities.add(key.substring(equals + 1, key.indexOf(':', equals)));
            }
        }
        return identities.size();
    }

    /** Whether a member path has its kind's form, and its file passes BridgeForge's evidence path rule. */
    static boolean wellFormed(String what, String path) {
        boolean shaped = switch (what) {
            case "csv-row" -> path.indexOf(" [") > 0 && path.endsWith("]");
            case "json-entry" -> path.indexOf('#') > 0;
            case "jar-class" -> path.indexOf('!') > 0 && path.indexOf('!') < path.length() - 1;
            default -> !path.isEmpty();
        };
        return shaped && BridgeForgeEvidenceHeader.pathProblem(JSON.getNodeFactory().textNode(memberFile(what, path))) == null;
    }

    private static ObjectNode unit(String member, String file, ExtractedString string) {
        ObjectNode unit = JSON.createObjectNode();
        unit.put("member", member);
        unit.put("file", file);
        unit.put("key", string.key());
        unit.put("text", string.originalText());
        return unit;
    }

    /**
     * CSV units in a row keyed by one of the gene's own ids that no member selected, in tables keyed by BridgeForge's key
     * column (review 2026-11-22: a table keyed by another column, such as console commands, says nothing about the gene).
     * A row of another entity that shares an id (a CUSTOM description named like a hull) can still appear: a candidate gap.
     */
    private static ArrayNode uncovered(JsonNode gene, Map<String, List<ExtractedString>> byFile, Set<String> selected) {
        Set<String> ids = new TreeSet<>();
        for (JsonNode provided : gene.get("provides")) {
            String anchor = provided.asText();
            ids.add(anchor.substring(anchor.indexOf(':') + 1));
        }
        ArrayNode found = JSON.createArrayNode();
        for (var file : byFile.entrySet()) {
            List<ExtractedString> strings = file.getValue();
            if (strings.isEmpty() || !keyedBy(strings.getFirst().key(), KEY_COLUMN)) {
                continue;
            }
            for (ExtractedString string : strings) {
                String rowId = csvRowId(string.key());
                if (rowId != null && ids.contains(rowId) && !selected.contains(file.getKey() + "\0" + string.key())) {
                    ObjectNode miss = found.addObject();
                    miss.put("file", file.getKey());
                    miss.put("key", string.key());
                }
            }
        }
        return found;
    }

    /** Whether the unit {@code key} is selected by the member ({@code what}, {@code path}). */
    static boolean selects(String what, String path, String key) {
        return switch (what) {
            case "file" -> true;
            case "csv-row" -> path.substring(path.indexOf(" [") + 2, path.length() - 1).equals(csvRowId(key));
            case "json-entry" -> {
                String pointer = path.substring(path.indexOf('#') + 1);
                yield key.equals("json:" + pointer) || key.startsWith("json:" + pointer + "/")
                        || key.equals("json-key:" + pointer) || key.startsWith("json-key:" + pointer + "/");
            }
            case "jar-class" -> {
                String entry = path.substring(path.indexOf('!') + 1);
                String className = entry.endsWith(".class") ? entry.substring(0, entry.length() - ".class".length()) : entry;
                yield key.startsWith("class:" + className + "#");
            }
            default -> false;
        };
    }

    /** The file a member lives in, as BridgeForge's licence ledger names it. */
    static String memberFile(String what, String path) {
        return switch (what) {
            case "csv-row" -> path.substring(0, path.indexOf(" ["));
            case "json-entry" -> path.substring(0, path.indexOf('#'));
            case "jar-class" -> path.substring(0, path.indexOf('!'));
            default -> path;
        };
    }

    /** The first identity value of a CSV unit key ({@code csv:<columns>=<identity>:<column>}), or null. */
    static String csvRowId(String key) {
        if (!key.startsWith("csv:")) {
            return null;
        }
        int equals = key.indexOf('=');
        int colon = equals < 0 ? -1 : key.indexOf(':', equals);
        if (colon < 0) {
            return null;
        }
        String identity = decode(key.substring(equals + 1, colon));
        int separator = identity.indexOf('\0');
        return separator < 0 ? identity : identity.substring(0, separator);
    }

    /** Whether a CSV unit key's first identity column is {@code column}. */
    static boolean keyedBy(String key, String column) {
        int equals = key.indexOf('=');
        if (!key.startsWith("csv:") || equals < 0) {
            return false;
        }
        String columns = decode(key.substring("csv:".length(), equals));
        int comma = columns.indexOf(',');
        return column.equals(comma < 0 ? columns : columns.substring(0, comma));
    }

    /** CsvExtractor's encoding, reversed: %00, %2C, %3A, %3D, then %25. */
    private static String decode(String value) {
        return value.replace("%00", "\0").replace("%2C", ",").replace("%3A", ":").replace("%3D", "=").replace("%25", "%");
    }

    private static String posix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sha256(Path file) throws ProjectException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        } catch (IOException exception) {
            throw new ProjectException("Could not hash member file " + file, exception);
        }
    }
}
