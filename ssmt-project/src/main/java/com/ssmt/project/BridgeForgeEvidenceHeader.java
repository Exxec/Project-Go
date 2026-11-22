package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * The header every evidence file about a BridgeForge gene carries (BridgeForge 94.G1 WP5, schema 1), judged here as
 * BridgeForge judges it. Ported 2026-11-22 from BridgeForge {@code genome/evidence.py} ({@code validate},
 * {@code path_problem}, {@code check} without re-hashing inputs) for Project Go P9, which writes such a header on the
 * strings it exports for a gene. Its fixtures live in {@code fixtures/gene-contract/evidence}.
 */
public final class BridgeForgeEvidenceHeader {
    /** The header's {@code evidence} value. */
    public static final String EVIDENCE = "bridgeforge-gene-evidence";
    /** The header's schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Gate states a header may carry. */
    public static final List<String> VERDICTS = List.of("PASS", "FAIL", "NOT_RUN", "UNKNOWN");
    static final List<String> REQUIRED_KEYS = List.of("evidence", "schema_version", "kind", "gene", "produced_by", "created_at",
            "build", "inputs", "verdict");

    private BridgeForgeEvidenceHeader() { }

    /** What keeps {@code evidence} from carrying a schema-1 header; empty when it does. Malformed input gives problems. */
    public static List<String> validate(JsonNode evidence) {
        if (evidence == null || !evidence.isObject()) {
            return List.of("an evidence file is a JSON object");
        }
        List<String> problems = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!evidence.has(key)) {
                problems.add("missing " + key);
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        JsonNode version = evidence.get("schema_version");
        if (!EVIDENCE.equals(BridgeForgeGeneManifest.text(evidence.get("evidence"))) || !version.isIntegralNumber()
                || version.asLong() != SCHEMA_VERSION) {
            problems.add("header is " + evidence.get("evidence") + " " + version + ", not " + EVIDENCE + " " + SCHEMA_VERSION);
        }
        String kind = BridgeForgeGeneManifest.text(evidence.get("kind"));
        if (kind == null || kind.isEmpty()) {
            problems.add("kind is a non-empty string");
        }
        JsonNode gene = evidence.get("gene");
        if (!gene.isObject() || !BridgeForgeGeneManifest.isHash(gene.get("identity")) || gene.get("schema_version") == null
                || !gene.get("schema_version").isIntegralNumber()) {
            problems.add("gene is {identity: 64 hex digits, schema_version: integer}");
        } else if (BridgeForgeGeneManifest.WITHDRAWN_VERSIONS.contains(gene.get("schema_version").asInt())) {
            problems.add("gene schema " + gene.get("schema_version") + " is withdrawn");
        }
        JsonNode produced = evidence.get("produced_by");
        if (!produced.isObject() || !nonEmptyText(produced.get("tool")) || !nonEmptyText(produced.get("version"))) {
            problems.add("produced_by is {tool, version} as non-empty strings");
        }
        if (!hasOffset(evidence.get("created_at"))) {
            problems.add("created_at is an ISO 8601 time with its UTC offset");
        }
        JsonNode build = evidence.get("build");
        if (!build.isObject() || build.has("game_version") && !build.get("game_version").isNull()
                && !build.get("game_version").isTextual()) {
            problems.add("build is an object whose game_version is a string or null");
        }
        inputsProblem(evidence.get("inputs"), problems);
        if (!VERDICTS.contains(BridgeForgeGeneManifest.text(evidence.get("verdict")))) {
            problems.add("verdict is one of " + String.join(", ", VERDICTS));
        }
        return problems;
    }

    /** What makes {@code evidence} not evidence about {@code gene}: a malformed header, another gene or another schema. */
    public static List<String> check(JsonNode evidence, JsonNode gene) {
        List<String> problems = validate(evidence);
        if (!problems.isEmpty()) {
            return problems;
        }
        List<String> found = new ArrayList<>();
        if (!evidence.get("gene").get("identity").asText().equals(BridgeForgeGeneManifest.text(gene.get("identity")))) {
            found.add("the evidence is about another gene (identity differs)");
        }
        if (!evidence.get("gene").get("schema_version").equals(gene.get("schema_version"))) {
            found.add("the evidence names gene schema " + evidence.get("gene").get("schema_version") + ", the manifest is "
                    + gene.get("schema_version"));
        }
        return found;
    }

    /** Why {@code path} is not a relative posix path inside its root, or null. */
    public static String pathProblem(JsonNode path) {
        String value = BridgeForgeGeneManifest.text(path);
        if (value == null || value.isEmpty() || !value.equals(value.strip())) {
            return "an input path is a non-empty string without surrounding spaces";
        }
        boolean badSegment = false;
        for (String segment : value.split("/", -1)) {
            badSegment |= segment.isEmpty() || ".".equals(segment) || "..".equals(segment);
        }
        if (value.contains("\\") || value.startsWith("/") || value.contains(":") || badSegment) {
            return "input path " + value + " must be relative, in posix form, without '.', '..' or empty segments";
        }
        return null;
    }

    private static void inputsProblem(JsonNode inputs, List<String> problems) {
        if (!inputs.isArray()) {
            problems.add("inputs is a list");
            return;
        }
        for (JsonNode row : inputs) {
            if (!row.isObject() || !nonEmptyText(row.get("role")) || !BridgeForgeGeneManifest.isHash(row.get("sha256"))) {
                problems.add("each input is {role, path, sha256: 64 lowercase hex digits}");
                return;
            }
            String problem = pathProblem(row.get("path"));
            if (problem != null) {
                problems.add(problem);
                return;
            }
        }
    }

    private static boolean nonEmptyText(JsonNode value) {
        return value != null && value.isTextual() && !value.asText().isEmpty();
    }

    /** Python's {@code datetime.fromisoformat} with a UTC offset; a space may stand for the {@code T}. */
    private static boolean hasOffset(JsonNode value) {
        String text = BridgeForgeGeneManifest.text(value);
        if (text == null) {
            return false;
        }
        try {
            OffsetDateTime.parse(text.length() > 10 && text.charAt(10) == ' ' ? text.substring(0, 10) + "T" + text.substring(11) : text);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
