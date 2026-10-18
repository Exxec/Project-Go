package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Tells identifiers from text. Every exported unit becomes {@code TEXT}, {@code IDENTIFIER} (never translated),
 * {@code LINKED} (translated together with every unit sharing its value, or not at all) or {@code UNKNOWN} (a JSON key
 * nothing else uses: translated only after review, the shared-method behaviour). The rules are data
 * ({@code unit-roles.json}) so a new ecosystem convention needs a rule line, not code.
 */
public final class UnitRoleClassifier {
    /** Unit roles. */
    public enum Role { TEXT, IDENTIFIER, LINKED, UNKNOWN }

    /**
     * Classification of one export.
     *
     * @param roles unit id to role
     * @param groups unit id to link group id (LINKED units only)
     * @param rules unit id to the id of the rule that decided it (absent for plain TEXT)
     * @param rulesVersion version of the rule data used
     */
    public record Result(Map<String, Role> roles, Map<String, String> groups, Map<String, String> rules,
            String rulesVersion) {
        public Role role(String id) {
            return roles.getOrDefault(id, Role.TEXT);
        }
    }

    private record ColumnRule(String id, Pattern pattern, boolean linkable) { }
    private record FieldRule(String id, Pattern pattern) { }
    private record Cell(String id, String kind, String source, Role base, boolean linkable, String rule) { }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RESOURCE = "/com/ssmt/project/unit-roles.json";
    private final List<ColumnRule> columns = new ArrayList<>();
    private final List<FieldRule> fields = new ArrayList<>();
    private final Role bareKeys;
    private final boolean valuesAreIds;
    private final String version;

    private UnitRoleClassifier(JsonNode rules) {
        version = rules.path("rules_version").asText("unversioned");
        for (JsonNode rule : rules.path("csv_columns")) {
            columns.add(new ColumnRule(rule.path("id").asText(), pattern(rule), rule.path("linkable").asBoolean()));
        }
        for (JsonNode rule : rules.path("json_fields")) {
            fields.add(new FieldRule(rule.path("id").asText(), pattern(rule)));
        }
        bareKeys = Role.valueOf(rules.path("bare_json_keys").asText("UNKNOWN"));
        if (bareKeys != Role.UNKNOWN && bareKeys != Role.IDENTIFIER) {
            throw new IllegalArgumentException("bare_json_keys must be UNKNOWN or IDENTIFIER");
        }
        valuesAreIds = rules.path("values_are_ids").asBoolean(true);
    }

    private static Pattern pattern(JsonNode rule) {
        return Pattern.compile(rule.path("pattern").asText(), Pattern.CASE_INSENSITIVE);
    }

    /** The rules shipped with the program. */
    public static UnitRoleClassifier withDefaultRules() {
        try (InputStream input = UnitRoleClassifier.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            return new UnitRoleClassifier(JSON.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + RESOURCE, exception);
        }
    }

    /** A classifier over caller-supplied rule data (same schema as {@code unit-roles.json}). */
    public static UnitRoleClassifier fromRules(JsonNode rules) {
        return new UnitRoleClassifier(rules);
    }

    /** Classifies every entry of a schema-v1 export. */
    public Result classify(JsonNode document) {
        return classify(document, Map.of());
    }

    /**
     * Classifies with bytecode call-site evidence.
     *
     * @param protectedLiterals {@code file|class|text} of every class literal that only lookups use (see
     *     {@link BytecodeCallSites#protectedLiterals}) to the id of the deciding rule
     */
    public Result classify(JsonNode document, Map<String, String> protectedLiterals) {
        List<Cell> cells = new ArrayList<>();
        for (JsonNode entry : document.path("entries")) {
            cells.add(base(entry, protectedLiterals));
        }
        Set<String> identifierValues = new HashSet<>();
        for (Cell cell : cells) {
            if (cell.base() == Role.IDENTIFIER && !cell.linkable()) {
                identifierValues.add(cell.source());
            }
        }
        if (valuesAreIds) {
            cells = withIdentifierColumns(cells, identifierValues);
        }
        Map<String, Role> roles = new LinkedHashMap<>();
        Map<String, String> rules = new HashMap<>();
        Map<String, String> groups = new HashMap<>();
        Map<String, List<Cell>> bySource = new LinkedHashMap<>();
        for (Cell cell : cells) {
            roles.put(cell.id(), cell.base());
            if (cell.rule() != null) {
                rules.put(cell.id(), cell.rule());
            }
            bySource.computeIfAbsent(cell.source(), ignored -> new ArrayList<>()).add(cell);
        }
        for (var sameValue : bySource.entrySet()) {
            link(sameValue.getKey(), sameValue.getValue(), identifierValues.contains(sameValue.getKey()),
                    roles, groups, rules);
        }
        if (bareKeys == Role.IDENTIFIER) {
            for (Cell cell : cells) {
                if (roles.get(cell.id()) == Role.UNKNOWN) {
                    roles.put(cell.id(), Role.IDENTIFIER);
                    rules.put(cell.id(), "bare-json-key");
                }
            }
        }
        return new Result(Map.copyOf(roles), Map.copyOf(groups), Map.copyOf(rules), version);
    }

    private Cell base(JsonNode entry, Map<String, String> protectedLiterals) {
        String id = entry.path("id").asText();
        String kind = entry.path("kind").asText();
        String source = entry.path("source").asText();
        if (kind.equals("jar") || kind.equals("class")) {
            String rule = protectedLiterals.get(entry.path("file").asText() + "|"
                    + entry.path("context").path("class").asText("") + "|" + source);
            if (rule != null) {
                return new Cell(id, kind, source, Role.IDENTIFIER, false, rule);
            }
        }
        if (kind.equals("csv")) {
            String column = entry.path("context").path("column").asText().strip();
            for (ColumnRule rule : columns) {
                if (rule.pattern().matcher(column).matches()) {
                    return new Cell(id, kind, source, Role.IDENTIFIER, rule.linkable(), rule.id());
                }
            }
        } else if (kind.equals("json")) {
            for (JsonNode element : entry.path("context").path("path")) {
                if (!element.isTextual()) {
                    continue;
                }
                for (FieldRule rule : fields) {
                    if (rule.pattern().matcher(element.asText()).matches()) {
                        return new Cell(id, kind, source, Role.IDENTIFIER, false, rule.id());
                    }
                }
            }
        } else if (kind.equals("json-key")) {
            return new Cell(id, kind, source, Role.UNKNOWN, false, "json-key");
        }
        return new Cell(id, kind, source, Role.TEXT, false, null);
    }

    /** A CSV column whose every CJK cell repeats an identifier value elsewhere in the mod holds identifiers. */
    private static List<Cell> withIdentifierColumns(List<Cell> cells, Set<String> identifierValues) {
        Map<String, List<Cell>> columnsByKey = new LinkedHashMap<>();
        for (Cell cell : cells) {
            if (cell.kind().equals("csv") && cell.base() == Role.TEXT) {
                columnsByKey.computeIfAbsent(columnKey(cell.id()), ignored -> new ArrayList<>()).add(cell);
            }
        }
        Set<String> promoted = new HashSet<>();
        for (List<Cell> column : columnsByKey.values()) {
            if (column.stream().allMatch(cell -> identifierValues.contains(cell.source()))) {
                column.forEach(cell -> promoted.add(cell.id()));
            }
        }
        List<Cell> result = new ArrayList<>();
        for (Cell cell : cells) {
            result.add(promoted.contains(cell.id())
                    ? new Cell(cell.id(), cell.kind(), cell.source(), Role.IDENTIFIER, false, "column-values-are-ids")
                    : cell);
        }
        return result;
    }

    /** CSV unit ids are {@code csv:<file>#<row>:<column>}: a column is the file plus the text after the last colon. */
    private static String columnKey(String id) {
        int hash = id.indexOf('#');
        int colon = id.lastIndexOf(':');
        return (hash < 0 ? id : id.substring(0, hash)) + "|" + (colon < 0 ? "" : id.substring(colon));
    }

    private static void link(String source, List<Cell> sameValue, boolean identifierValue, Map<String, Role> roles,
            Map<String, String> groups, Map<String, String> rules) {
        List<Cell> anchors = sameValue.stream().filter(cell -> cell.kind().equals("json-key")
                || cell.kind().equals("class") || cell.kind().equals("jar")).toList();
        if (anchors.isEmpty()) {
            return;
        }
        if (identifierValue) {
            // The value is also a plain identifier (an id column): whatever else uses it must not move alone.
            for (Cell cell : sameValue) {
                if (anchors.contains(cell) || cell.base() == Role.IDENTIFIER && cell.linkable()) {
                    roles.put(cell.id(), Role.IDENTIFIER);
                    rules.put(cell.id(), "shares-value-with-identifier");
                }
            }
            return;
        }
        List<Cell> members = sameValue.stream()
                .filter(cell -> cell.base() != Role.IDENTIFIER || cell.linkable()).toList();
        if (members.size() < 2) {
            return;
        }
        String group = "link:" + shortHash(source);
        for (Cell cell : members) {
            roles.put(cell.id(), Role.LINKED);
            groups.put(cell.id(), group);
            rules.put(cell.id(), "linked-by-shared-value");
        }
    }

    private static String shortHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /**
     * Adds {@code role}, {@code role_rule} and (for linked units) {@code group} to every entry that is not plain text.
     * Plain text carries no field: absence means TEXT.
     */
    public ObjectNode annotate(ObjectNode document) {
        return annotate(document, Map.of());
    }

    /** Same, with bytecode call-site evidence. */
    public ObjectNode annotate(ObjectNode document, Map<String, String> protectedLiterals) {
        Result result = classify(document, protectedLiterals);
        for (JsonNode node : document.path("entries")) {
            ObjectNode entry = (ObjectNode) node;
            String id = entry.path("id").asText();
            Role role = result.role(id);
            if (role != Role.TEXT) {
                entry.put("role", role.name());
                if (result.rules().containsKey(id)) {
                    entry.put("role_rule", result.rules().get(id));
                }
                if (result.groups().containsKey(id)) {
                    entry.put("group", result.groups().get(id));
                }
            }
        }
        document.put("roles_version", result.rulesVersion());
        return document;
    }

    /**
     * Keys {@code file|kind|source} of the units that are protected identifiers. The key does not use the unit id,
     * which can shift after a partial translation (JSON ordinals count only CJK values).
     */
    public Set<String> protectedUnitKeys(JsonNode document) {
        return protectedUnitKeys(document, Map.of());
    }

    /** Same, with bytecode call-site evidence. */
    public Set<String> protectedUnitKeys(JsonNode document, Map<String, String> protectedLiterals) {
        Result result = classify(document, protectedLiterals);
        Set<String> keys = new HashSet<>();
        for (JsonNode entry : document.path("entries")) {
            if (result.role(entry.path("id").asText()) == Role.IDENTIFIER) {
                keys.add(unitKey(entry));
            }
        }
        return keys;
    }

    /** Shift-proof identity of a unit for comparing an export with the export of its translated output. */
    public static String unitKey(JsonNode entry) {
        return entry.path("file").asText() + "|" + entry.path("kind").asText() + "|" + entry.path("source").asText();
    }

    /** Link groups with their member ids, sorted. */
    public static Map<String, List<String>> members(Result result) {
        Map<String, List<String>> byGroup = new TreeMap<>();
        result.groups().forEach((id, group) -> byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(id));
        byGroup.values().forEach(Collections::sort);
        return byGroup;
    }
}
