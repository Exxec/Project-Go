package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.core.model.TranslationProvenance;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Approval by rules instead of by hand (owner decision 2026-10-14). A draft translation is approved, without a person,
 * only when every rule holds: the unit is plain {@code TEXT} (never an identifier, linked or unknown unit), the
 * placeholders, markers and option syntax are intact (the import validators, run before this), line breaks match,
 * glossary terms are used consistently, no CJK is left, and the length ratio is within the bounds of the unit kind.
 * Approved units carry {@link TranslationProvenance#POLICY_APPROVED} with the rule version and stay reversible.
 */
public final class PolicyApproval {
    /** Outcome for one unit. */
    public record Decision(boolean approved, String reason) {
        static Decision yes() {
            return new Decision(true, "");
        }

        static Decision no(String reason) {
            return new Decision(false, reason);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final JsonNode rules;

    private PolicyApproval(JsonNode rules) {
        this.rules = rules;
    }

    /** The rules shipped with the program. */
    public static PolicyApproval withDefaultRules() {
        try (InputStream input = PolicyApproval.class.getResourceAsStream("/com/ssmt/project/approval-rules.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing approval-rules.json");
            }
            return new PolicyApproval(JSON.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read approval-rules.json", exception);
        }
    }

    /** Caller-supplied rule data (same schema as {@code approval-rules.json}). */
    public static PolicyApproval fromRules(JsonNode data) {
        return new PolicyApproval(data);
    }

    /** Version recorded with every approval. */
    public String rulesVersion() {
        return rules.path("rules_version").asText("unversioned");
    }

    /**
     * Decides one unit.
     *
     * @param role the unit's role from the method document ({@code TEXT} when absent)
     * @param kind unit kind (csv, json, java, ...)
     * @param source exact source text
     * @param translation validated translation
     * @param glossary approved glossary: source term to target
     */
    public Decision evaluate(String role, String kind, String source, String translation,
            Map<String, String> glossary) {
        if (!role.equals("TEXT")) {
            return Decision.no("role " + role + " is never approved by rules");
        }
        if (translation.isBlank() || translation.equals(source)) {
            return Decision.no("translation is empty or equals the source");
        }
        if (BridgeForgeTextUnits.cjk(translation)) {
            return Decision.no("translation still contains CJK text");
        }
        if (lineBreaks(source) != lineBreaks(translation)) {
            return Decision.no("line breaks differ");
        }
        String glossaryProblem = glossaryProblem(source, translation, glossary);
        if (glossaryProblem != null) {
            return Decision.no(glossaryProblem);
        }
        double ratio = (double) translation.codePointCount(0, translation.length())
                / Math.max(1, source.codePointCount(0, source.length()));
        JsonNode bounds = rules.path("length_ratio").has(kind)
                ? rules.path("length_ratio").path(kind) : rules.path("length_ratio").path("default");
        double max = bounds.path("max").asDouble(12.0);
        if (source.codePointCount(0, source.length()) <= rules.path("short_source_chars").asInt(4)) {
            max = Math.max(max, rules.path("short_source_max").asDouble(max));
        }
        if (ratio < bounds.path("min").asDouble(0.5) || ratio > max) {
            return Decision.no(String.format(Locale.ROOT, "length ratio %.1f outside %.1f..%.1f for %s", ratio,
                    bounds.path("min").asDouble(0.5), max, kind));
        }
        return Decision.yes();
    }

    private static String glossaryProblem(String source, String translation, Map<String, String> glossary) {
        String lower = translation.toLowerCase(Locale.ROOT);
        for (var term : glossary.entrySet()) {
            if (term.getKey().length() < 2 || !source.contains(term.getKey()) || term.getValue().isBlank()) {
                continue;
            }
            boolean whole = source.equals(term.getKey());
            boolean used = whole ? lower.strip().equals(term.getValue().toLowerCase(Locale.ROOT).strip())
                    : lower.contains(term.getValue().toLowerCase(Locale.ROOT));
            if (!used) {
                return "glossary term '" + term.getKey() + "' is not rendered as '" + term.getValue() + "'";
            }
        }
        return null;
    }

    private static int lineBreaks(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r' && (index + 1 >= value.length() || value.charAt(index + 1) != '\n')) {
                count++;
            }
        }
        return count;
    }

    /** Unit ids to their role in a method document; units without a role field are TEXT. */
    static Map<String, String> rolesOf(LocalizationProject project) {
        var document = project.methodDocument();
        Map<String, String> roles = new java.util.HashMap<>();
        if (document != null) {
            for (JsonNode entry : document.path("entries")) {
                roles.put(entry.path("id").asText(), entry.path("role").asText("TEXT"));
            }
        }
        return roles;
    }

    /** Unit ids to their kind in a method document. */
    static Map<String, String> kindsOf(LocalizationProject project) {
        var document = project.methodDocument();
        Map<String, String> kinds = new java.util.HashMap<>();
        if (document != null) {
            for (JsonNode entry : document.path("entries")) {
                kinds.put(entry.path("id").asText(), entry.path("kind").asText());
            }
        }
        return kinds;
    }

    /** Glossary map of the method document; empty when there is none. */
    static Map<String, String> glossaryOf(LocalizationProject project) {
        var document = project.methodDocument();
        Map<String, String> glossary = new java.util.LinkedHashMap<>();
        if (document != null) {
            document.path("glossary").fields().forEachRemaining(field -> glossary.put(field.getKey(),
                    field.getValue().asText()));
        }
        return glossary;
    }

    /**
     * Returns the project with every POLICY_APPROVED entry back to a reviewable draft (AI_TRANSLATED); the
     * translation text is kept, only the approval is withdrawn.
     */
    public static LocalizationProject revoke(LocalizationProject project) {
        List<ProjectEntry> entries = project.entries().stream()
                .map(entry -> entry.provenance() == TranslationProvenance.POLICY_APPROVED
                        ? entry.withTranslation(entry.translatedText(), TranslationProvenance.AI_TRANSLATED) : entry)
                .toList();
        return project.withEntries(entries);
    }
}
