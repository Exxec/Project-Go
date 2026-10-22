package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * H4 carry-forward on a mod update. Given the project of the previous mod version and a freshly extracted project of the
 * new version, an approval is reused only for a unit whose source identity, source digest and context are all unchanged.
 * Everything else goes to review with the old translation shown as a hint only. A LINKED group is carried only when
 * every member qualifies and the old members agreed on one text; otherwise the whole group goes to review.
 *
 * <p>Definitions (also in USER_GUIDE.md):
 * <ul>
 * <li>identity: normalized source file plus stable key;</li>
 * <li>source digest: SHA-256 of the exact UTF-8 source text;</li>
 * <li>context: unit kind, role, the extractor's context object (path, column, class and so on, canonical JSON) and,
 * for a LINKED unit, the sorted identities of every member of its group;</li>
 * <li>approval: a non-blank translation whose provenance is HUMAN_EDITED or POLICY_APPROVED.</li>
 * </ul>
 * Pure: neither input project is modified, and the output is deterministic.
 */
public final class CarryForwardService {
    /** Classification of one unit. */
    public enum Status {
        /** Identity, digest and context unchanged and approved: the approval is carried. */
        CARRIED,
        /** Same identity, different digest or context: review, old translation as hint. */
        CHANGED,
        /** Identity, digest and context unchanged, but the old unit had no approval: nothing to carry. */
        UNCHANGED_UNAPPROVED,
        /** Qualified on its own but its LINKED group did not qualify as a whole: review, hint only. */
        GROUP_HELD,
        /** Same digest found only under a different identity: review, hint only. */
        MOVED,
        /** No old unit with this identity or digest. */
        NEW,
        /** Old unit with no counterpart in the new version (listed only). */
        REMOVED
    }

    /**
     * One classified unit.
     *
     * @param status classification
     * @param file source file
     * @param key stable key
     * @param digest SHA-256 of the unit's source text (old text for REMOVED)
     * @param reason why the unit was not carried, empty for CARRIED
     * @param hints old translations offered for review (the carried approval itself for CARRIED)
     */
    public record Finding(Status status, String file, String key, String digest, String reason, List<Hint> hints) {
        public Finding {
            hints = List.copyOf(hints);
        }
    }

    /**
     * An old translation shown to the reviewer; never applied except for CARRIED units.
     *
     * @param file old source file
     * @param key old stable key
     * @param translation old translated text
     * @param provenance old provenance
     * @param approved whether the old entry counted as approved
     */
    public record Hint(String file, String key, String translation, String provenance, boolean approved) { }

    /**
     * Result of one carry-forward.
     *
     * @param project new project with approvals carried; everything else exactly as extracted
     * @param findings sorted findings
     * @param oldProjectSha256 digest of the old project's canonical serialization (recorded as provenance)
     * @param newProjectSha256 digest of the new project's canonical serialization
     */
    public record Result(LocalizationProject project, List<Finding> findings, String oldProjectSha256,
            String newProjectSha256) {
        public Result {
            findings = List.copyOf(findings);
        }

        /** Count of one status. */
        public long count(Status status) {
            return findings.stream().filter(finding -> finding.status() == status).count();
        }
    }

    private record Unit(ProjectEntry entry, String context, Set<String> members) { }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEPARATOR = "\u0000";

    /**
     * Classifies and carries.
     *
     * @param oldProject project of the previous mod version (never modified)
     * @param newProject freshly extracted project of the new version
     * @return carried project and findings
     * @throws ProjectException when the projects are for different mods
     */
    public Result carry(LocalizationProject oldProject, LocalizationProject newProject) throws ProjectException {
        if (!oldProject.sourceModId().equals(newProject.sourceModId())) {
            throw new ProjectException("Old and new project are for different source mod ids");
        }
        Map<String, Unit> old = units(oldProject);
        Map<String, Unit> current = units(newProject);
        Map<String, List<Unit>> goneByDigest = new TreeMap<>();
        old.forEach((id, unit) -> {
            if (!current.containsKey(id)) {
                goneByDigest.computeIfAbsent(digest(unit.entry().originalText()), ignored -> new ArrayList<>())
                        .add(unit);
            }
        });
        Map<String, Finding> findings = new TreeMap<>();
        Set<String> carriable = new TreeSet<>();
        Set<String> movedFrom = new TreeSet<>();
        current.forEach((id, unit) -> {
            Unit before = old.get(id);
            Finding finding = before != null ? sameIdentity(unit, before) : moved(unit, goneByDigest, movedFrom);
            if (finding.status() == Status.CARRIED) {
                carriable.add(id);
            }
            findings.put(id, finding);
        });
        holdIncompleteGroups(current, old, carriable, findings);
        List<Finding> all = new ArrayList<>(findings.values());
        old.forEach((id, unit) -> {
            if (!current.containsKey(id) && !movedFrom.contains(id)) {
                all.add(new Finding(Status.REMOVED, file(unit.entry()), unit.entry().key(),
                        digest(unit.entry().originalText()), "not in the new version", List.of(hint(unit.entry()))));
            }
        });
        all.sort(Comparator.comparing(Finding::file).thenComparing(Finding::key));
        List<ProjectEntry> entries = new ArrayList<>();
        for (ProjectEntry entry : newProject.entries()) {
            String id = identity(entry);
            ProjectEntry before = carriable.contains(id) ? old.get(id).entry() : null;
            entries.add(before == null ? entry : entry.withTranslation(before.translatedText(), before.provenance()));
        }
        return new Result(newProject.withEntries(entries), all, projectDigest(oldProject), projectDigest(newProject));
    }

    private static Finding sameIdentity(Unit unit, Unit before) {
        ProjectEntry entry = unit.entry();
        String digest = digest(entry.originalText());
        String file = file(entry);
        List<Hint> hint = before.entry().translatedText().isBlank() ? List.of() : List.of(hint(before.entry()));
        if (!digest.equals(digest(before.entry().originalText()))) {
            return new Finding(Status.CHANGED, file, entry.key(), digest, "source text changed", hint);
        }
        if (!unit.context().equals(before.context())) {
            return new Finding(Status.CHANGED, file, entry.key(), digest, "context changed", hint);
        }
        if (!approved(before.entry())) {
            return new Finding(Status.UNCHANGED_UNAPPROVED, file, entry.key(), digest,
                    "old unit had no approval", hint);
        }
        return new Finding(Status.CARRIED, file, entry.key(), digest, "", hint);
    }

    private static Finding moved(Unit unit, Map<String, List<Unit>> goneByDigest, Set<String> movedFrom) {
        ProjectEntry entry = unit.entry();
        String digest = digest(entry.originalText());
        List<Unit> sources = goneByDigest.getOrDefault(digest, List.of());
        if (sources.isEmpty()) {
            return new Finding(Status.NEW, file(entry), entry.key(), digest, "no old unit", List.of());
        }
        List<Hint> hints = new ArrayList<>();
        for (Unit source : sources) {
            movedFrom.add(identity(source.entry()));
            if (!source.entry().translatedText().isBlank()) {
                hints.add(hint(source.entry()));
            }
        }
        return new Finding(Status.MOVED, file(entry), entry.key(), digest,
                "same source text under a different identity", hints);
    }

    private static void holdIncompleteGroups(Map<String, Unit> current, Map<String, Unit> old, Set<String> carriable,
            Map<String, Finding> findings) {
        Set<String> held = new TreeSet<>();
        for (String id : carriable) {
            Set<String> members = current.get(id).members();
            Set<String> texts = new TreeSet<>();
            boolean complete = true;
            for (String member : members) {
                complete &= carriable.contains(member);
                if (old.containsKey(member)) {
                    texts.add(old.get(member).entry().translatedText());
                }
            }
            if (!complete || texts.size() > 1) {
                held.add(id);
            }
        }
        for (String id : held) {
            Finding finding = findings.get(id);
            carriable.remove(id);
            findings.put(id, new Finding(Status.GROUP_HELD, finding.file(), finding.key(), finding.digest(),
                    "a member of its LINKED group did not qualify or the old members disagreed", finding.hints()));
        }
    }

    private static Map<String, Unit> units(LocalizationProject project) {
        Map<String, JsonNode> byKey = new HashMap<>();
        Map<String, Set<String>> groupMembers = new HashMap<>();
        JsonNode document = project.methodDocument();
        if (document != null) {
            for (JsonNode node : document.path("entries")) {
                String key = "bf:" + node.path("id").asText();
                byKey.put(key, node);
                if (node.path("role").asText().equals("LINKED")) {
                    groupMembers.computeIfAbsent(node.path("group").asText(), ignored -> new TreeSet<>())
                            .add(node.path("file").asText() + SEPARATOR + key);
                }
            }
        }
        Map<String, Unit> result = new TreeMap<>();
        for (ProjectEntry entry : project.entries()) {
            JsonNode node = byKey.get(entry.key());
            Set<String> members = node != null && node.path("role").asText().equals("LINKED")
                    ? groupMembers.get(node.path("group").asText()) : Set.of();
            String context = "";
            if (node != null) {
                ObjectNode facts = JSON.createObjectNode();
                facts.put("kind", node.path("kind").asText());
                facts.put("role", node.path("role").asText("TEXT"));
                facts.set("context", node.path("context"));
                context = canonical(facts) + "|" + String.join(",", members);
            }
            result.put(identity(entry), new Unit(entry, context, Set.copyOf(members)));
        }
        return result;
    }

    private static String canonical(JsonNode node) {
        StringBuilder out = new StringBuilder();
        if (node.isObject()) {
            out.append('{');
            Set<String> names = new TreeSet<>();
            Iterator<String> iterator = node.fieldNames();
            iterator.forEachRemaining(names::add);
            names.forEach(name -> out.append(JSON.valueToTree(name).toString()).append(':')
                    .append(canonical(node.get(name))).append(','));
            return out.append('}').toString();
        }
        if (node.isArray()) {
            out.append('[');
            node.forEach(child -> out.append(canonical(child)).append(','));
            return out.append(']').toString();
        }
        return node.toString();
    }

    private static boolean approved(ProjectEntry entry) {
        return !entry.translatedText().isBlank()
                && (entry.provenance() == TranslationProvenance.HUMAN_EDITED
                        || entry.provenance() == TranslationProvenance.POLICY_APPROVED);
    }

    private static Hint hint(ProjectEntry entry) {
        return new Hint(file(entry), entry.key(), entry.translatedText(), entry.provenance().name(), approved(entry));
    }

    private static String identity(ProjectEntry entry) {
        return file(entry) + SEPARATOR + entry.key();
    }

    private static String file(ProjectEntry entry) {
        return entry.sourceFile().toString().replace('\\', '/');
    }

    /**
     * SHA-256 of the exact UTF-8 source text.
     *
     * @param text source text
     * @return lowercase hex digest
     */
    public static String digest(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String projectDigest(LocalizationProject project) throws ProjectException {
        return sha256(new LocalizationProjectService().serialize(project));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Deterministic JSON report (no timestamps).
     *
     * @param result carry-forward result
     * @return report document
     */
    public static ObjectNode toJson(Result result) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schema", "project-go-carry-forward-1");
        root.put("oldProjectSha256", result.oldProjectSha256());
        root.put("newProjectSha256", result.newProjectSha256());
        ObjectNode counts = root.putObject("counts");
        for (Status status : Status.values()) {
            counts.put(status.name(), result.count(status));
        }
        ArrayNode units = root.putArray("units");
        for (Finding finding : result.findings()) {
            ObjectNode node = units.addObject();
            node.put("status", finding.status().name());
            node.put("file", finding.file());
            node.put("key", finding.key());
            node.put("sourceDigest", finding.digest());
            node.put("reason", finding.reason());
            ArrayNode hints = node.putArray(finding.status() == Status.CARRIED ? "carriedFrom" : "hints");
            for (Hint hint : finding.hints()) {
                hints.addObject().put("file", hint.file()).put("key", hint.key())
                        .put("translation", hint.translation()).put("provenance", hint.provenance())
                        .put("approved", hint.approved())
                        .put("oldProjectSha256", result.oldProjectSha256());
            }
        }
        return root;
    }

    /**
     * Short human summary.
     *
     * @param result carry-forward result
     * @return plain text
     */
    public static String summary(Result result) {
        StringBuilder text = new StringBuilder("Carry-forward report\n");
        for (Status status : Status.values()) {
            text.append(String.format(Locale.ROOT, "  %-20s %d%n", status, result.count(status)));
        }
        text.append("Only CARRIED units reuse an approval; every other unit with a hint needs review.\n");
        for (Finding finding : result.findings()) {
            if (finding.status() != Status.CARRIED) {
                text.append(String.format(Locale.ROOT, "%s %s %s: %s%n", finding.status(), finding.file(),
                        finding.key(), finding.reason()));
            }
        }
        return text.toString();
    }
}
