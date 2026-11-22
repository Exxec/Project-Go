package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * BridgeForge's gene manifest ({@code gene.json}, schema 4), judged here as BridgeForge judges it. Ported 2026-11-22 from
 * BridgeForge {@code genome/gene.py} ({@code validate}, {@code identity}; ROADMAP-3 94.G1 WP1 and 94.G2 schema 4) for
 * Project Go P9. The contract is pinned by hash in {@code bridgeforge-pins/gene-contract.sha256} and copied byte for byte
 * to {@code fixtures/gene-contract}; the conformance test judges every fixture there and must agree with BridgeForge's
 * verdict, so a rule change in BridgeForge needs the same change here.
 */
public final class BridgeForgeGeneManifest {
    /** The manifest's {@code schema} value. */
    public static final String SCHEMA = "bridgeforge-gene";
    /** The schema version this reader accepts. */
    public static final int SCHEMA_VERSION = 4;
    /** Schema versions BridgeForge withdrew; a manifest or evidence file naming one is refused. */
    public static final List<Integer> WITHDRAWN_VERSIONS = List.of(1, 2, 3);
    /** Gene kinds: BridgeForge's content kinds, then its campaign kinds. */
    public static final List<String> KINDS = List.of("hullmod", "weapon", "wing", "hull", "variant", "shipsystem", "faction",
            "star-system", "market", "industry", "condition", "ability", "rule", "combat-plugin", "campaign-plugin",
            "custom-start");
    /** Member kinds. */
    public static final List<String> MEMBER_KINDS = List.of("file", "csv-row", "jar-class", "json-entry");
    static final List<String> REQUIRED_KEYS = List.of("schema", "schema_version", "identity", "kind", "anchors", "provides",
            "members", "needs", "registrations", "conditions", "persistent_state", "build", "provenance", "derived_from",
            "licence", "phenotype_obligations", "unknowns", "collected_from");
    private static final List<String> LIST_KEYS = List.of("anchors", "provides", "registrations", "conditions",
            "persistent_state", "phenotype_obligations", "collected_from");
    private static final Map<String, List<String>> ELEMENT_FIELDS = Map.of("registrations", List.of("locus", "statement"),
            "conditions", List.of("kind", "subject"), "persistent_state", List.of("name", "via"));
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    /** Python compares strings by code point; Java's {@code compareTo} by UTF-16 unit. */
    private static final Comparator<String> CODE_POINTS = (left, right) -> {
        int[] a = left.codePoints().toArray();
        int[] b = right.codePoints().toArray();
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return Integer.compare(a.length, b.length);
    };

    private BridgeForgeGeneManifest() { }

    /** What keeps {@code gene} from being a schema-4 manifest; empty when it is one. Malformed input gives problems. */
    public static List<String> validate(JsonNode gene) {
        if (gene == null || !gene.isObject()) {
            return List.of("a manifest is a JSON object");
        }
        List<String> problems = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!gene.has(key)) {
                problems.add("missing " + key);
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        JsonNode version = gene.get("schema_version");
        if (!SCHEMA.equals(text(gene.get("schema"))) || !version.isIntegralNumber() || version.asLong() != SCHEMA_VERSION) {
            problems.add("schema is " + gene.get("schema") + " " + version + ", not " + SCHEMA + " " + SCHEMA_VERSION);
        }
        String kind = text(gene.get("kind"));
        if (kind == null || !KINDS.contains(kind)) {
            problems.add("kind " + gene.get("kind") + " is not a gene kind");
        }
        for (String key : LIST_KEYS) {
            if (!gene.get(key).isArray()) {
                problems.add(key + " is a list");
            }
        }
        anchorProblem(gene, kind, problems);
        objectWithLists(gene.get("needs"), "needs", List.of("vanilla", "mods"), problems);
        objectWithLists(gene.get("unknowns"), "unknowns", List.of("missing", "suspects"), problems);
        for (String key : List.of("build", "provenance")) {
            if (!gene.get(key).isObject()) {
                problems.add(key + " is an object");
            }
        }
        for (var element : ELEMENT_FIELDS.entrySet()) {
            problems.addAll(elementProblems(gene.get(element.getKey()), element.getKey(), element.getValue()));
        }
        String derived = derivedProblem(gene.get("derived_from"));
        if (derived != null) {
            problems.add(derived);
        }
        JsonNode members = gene.get("members");
        String licence = licenceProblem(gene.get("licence"), memberPaths(members));
        if (licence != null) {
            problems.add(licence);
        }
        membersProblems(gene, kind, members, problems);
        return problems;
    }

    /** The gene's identity: SHA-256 of the kind and its members (what, path, content hash) sorted by (what, path). */
    public static String identity(String kind, JsonNode members) {
        List<JsonNode> sorted = new ArrayList<>();
        members.forEach(sorted::add);
        sorted.sort(Comparator.<JsonNode, String>comparing(m -> m.get("what").asText(), CODE_POINTS)
                .thenComparing(m -> m.get("path").asText(), CODE_POINTS));
        StringBuilder text = new StringBuilder(kind).append('\n');
        for (JsonNode member : sorted) {
            text.append(member.get("what").asText()).append('\t').append(member.get("path").asText()).append('\t')
                    .append(member.get("sha256").asText()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static boolean isHash(JsonNode value) {
        return value != null && value.isTextual() && HASH.matcher(value.asText()).matches();
    }

    static String text(JsonNode value) {
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static void anchorProblem(JsonNode gene, String kind, List<String> problems) {
        JsonNode anchors = gene.get("anchors");
        if (!anchors.isArray()) {
            return;
        }
        boolean good = !anchors.isEmpty();
        for (JsonNode anchor : anchors) {
            good &= anchor.isTextual() && kind != null && anchor.asText().startsWith(kind + ":");
        }
        if (!good) {
            problems.add("anchors are one or more '" + kind + ":<id>' strings");
        }
    }

    private static void objectWithLists(JsonNode value, String key, List<String> fields, List<String> problems) {
        boolean good = value.isObject();
        for (String field : fields) {
            good &= value.isObject() && value.has(field) && value.get(field).isArray();
        }
        if (!good) {
            problems.add(key + " is an object with lists " + String.join(", ", fields));
            return;
        }
        if ("needs".equals(key) && value.has("genes")) {
            JsonNode genes = value.get("genes");
            boolean anchors = genes.isArray();
            for (JsonNode gene : genes) {
                anchors &= gene.isTextual() && gene.asText().contains(":");
            }
            if (!anchors) {
                problems.add("needs.genes, when present, is a list of 'kind:id' anchors");
            }
        }
    }

    private static List<String> elementProblems(JsonNode list, String key, List<String> fields) {
        TreeSet<String> found = new TreeSet<>();
        if (!list.isArray()) {
            return List.of();
        }
        for (JsonNode element : list) {
            boolean good = element.isObject();
            for (String field : fields) {
                good &= element.isObject() && text(element.get(field)) != null && !element.get(field).asText().isEmpty();
            }
            if (!good) {
                found.add("each of " + key + " is an object with non-empty strings " + String.join(", ", fields));
            }
        }
        return List.copyOf(found);
    }

    private static String derivedProblem(JsonNode derived) {
        if (derived.isNull()) {
            return null;
        }
        boolean good = derived.isObject() && isHash(derived.get("identity")) && text(derived.get("by")) != null
                && !derived.get("by").asText().isEmpty() && derived.has("from_build")
                && (derived.get("from_build").isNull() || derived.get("from_build").isTextual());
        return good ? null : "derived_from is null or {identity: 64 hex digits, by: non-empty string, from_build: string or null}";
    }

    private static Set<String> memberPaths(JsonNode members) {
        if (!members.isArray()) {
            return null;
        }
        Set<String> paths = new HashSet<>();
        for (JsonNode member : members) {
            if (member.isObject() && text(member.get("path")) != null) {
                paths.add(member.get("path").asText());
            }
        }
        return paths;
    }

    private static String licenceProblem(JsonNode licence, Set<String> memberPaths) {
        if (!licence.isObject() || text(licence.get("decision")) == null || text(licence.get("mod_decision")) == null) {
            return "licence is an object with a decision and a mod_decision";
        }
        JsonNode local = licence.get("local_only_members");
        boolean strings = local != null && local.isArray();
        Set<String> paths = new HashSet<>();
        if (strings) {
            for (JsonNode path : local) {
                strings &= path.isTextual();
                paths.add(path.asText());
            }
        }
        if (!strings) {
            return "licence.local_only_members is a list of member paths";
        }
        if (!paths.isEmpty() && !"LOCAL_ONLY".equals(licence.get("decision").asText())) {
            return "a gene with local-only members has the decision LOCAL_ONLY";
        }
        if (memberPaths != null && !memberPaths.containsAll(paths)) {
            return "licence.local_only_members names a path that is not a member";
        }
        return null;
    }

    private static void membersProblems(JsonNode gene, String kind, JsonNode members, List<String> problems) {
        if (!members.isArray() || members.isEmpty()) {
            problems.add("members is a non-empty list");
            return;
        }
        List<String> memberProblems = new ArrayList<>();
        for (JsonNode member : members) {
            String problem = memberProblem(member);
            if (problem != null) {
                memberProblems.add(problem);
            }
        }
        problems.addAll(memberProblems.subList(0, Math.min(3, memberProblems.size())));
        if (!memberProblems.isEmpty()) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (JsonNode member : members) {
            if (!keys.add(member.get("what").asText() + "\0" + member.get("path").asText())) {
                problems.add("a member is listed twice");
                break;
            }
        }
        if (!isHash(gene.get("identity")) || kind == null || !gene.get("identity").asText().equals(identity(kind, members))) {
            problems.add("identity does not match the members");
        }
    }

    private static String memberProblem(JsonNode member) {
        if (!member.isObject()) {
            return "a member is an object";
        }
        String what = text(member.get("what"));
        if (what == null || !MEMBER_KINDS.contains(what)) {
            return "member kind " + member.get("what") + " is not one of " + String.join(", ", MEMBER_KINDS);
        }
        String path = text(member.get("path"));
        if (path == null || path.isEmpty()) {
            return "a member's path is a non-empty string";
        }
        if ("json-entry".equals(what)) {
            String problem = jsonEntryProblem(path);
            if (problem != null) {
                return problem;
            }
        }
        if (!isHash(member.get("sha256"))) {
            return path + ": sha256 is 64 lowercase hex digits";
        }
        return null;
    }

    /** A json-entry path is {@code <file>#<JSON Pointer>} to an object key, never an array element. */
    private static String jsonEntryProblem(String path) {
        int hash = path.indexOf('#');
        if (hash <= 0) {
            return path + ": a json-entry path is <file>#<JSON Pointer>";
        }
        String pointer = path.substring(hash + 1);
        if (!pointer.startsWith("/")) {
            return path + ": the JSON Pointer starts with '/'";
        }
        String last = pointer.substring(pointer.lastIndexOf('/') + 1);
        if ("-".equals(last) || !last.isEmpty() && last.chars().allMatch(Character::isDigit)) {
            return path + ": a json-entry points at an object key, not an array element";
        }
        return null;
    }
}
