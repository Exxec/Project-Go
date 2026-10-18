package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the rule for linked units: every member of a link group (units sharing one value that code or a map key also
 * uses) is translated with the same text, or none is. A member without a translation takes the group's text; members
 * with different texts are all refused; two different groups may not end up with the same text (their lookups would
 * collide).
 */
final class LinkedGroupEnforcer {
    private LinkedGroupEnforcer() { }

    /**
     * Rewrites {@code byFile} (file to unit id to resolved translation) in place.
     *
     * @param roles classification of the current source
     * @param discovered unit id to the current source entry (for the file of a member that had no translation)
     * @param byFile translations about to be applied
     * @param problems receives one line per refused unit
     */
    static void enforce(UnitRoleClassifier.Result roles, Map<String, JsonNode> discovered,
            Map<String, Map<String, String>> byFile, ArrayNode problems) {
        Map<String, String> supplied = new HashMap<>();
        byFile.values().forEach(units -> units.forEach((id, text) -> {
            if (roles.groups().containsKey(id)) {
                supplied.put(id, text);
            }
        }));
        Map<String, String> chosen = new HashMap<>();
        for (var group : UnitRoleClassifier.members(roles).entrySet()) {
            List<String> members = group.getValue();
            Set<String> texts = new LinkedHashSet<>();
            members.stream().map(supplied::get).filter(java.util.Objects::nonNull).forEach(texts::add);
            if (texts.isEmpty()) {
                continue;
            }
            if (texts.size() > 1) {
                members.forEach(id -> refuse(byFile, problems, id, "linked group translated inconsistently"));
                continue;
            }
            chosen.put(group.getKey(), texts.iterator().next());
        }
        Map<String, String> owner = new HashMap<>();
        for (var group : chosen.entrySet()) {
            String previous = owner.putIfAbsent(group.getValue(), group.getKey());
            if (previous != null) {
                for (String other : List.of(previous, group.getKey())) {
                    UnitRoleClassifier.members(roles).get(other)
                            .forEach(id -> refuse(byFile, problems, id, "linked groups would share one translation"));
                }
            }
        }
        for (var group : chosen.entrySet()) {
            for (String id : UnitRoleClassifier.members(roles).get(group.getKey())) {
                if (!supplied.containsKey(id) && !refused(problems, id)) {
                    byFile.computeIfAbsent(discovered.get(id).path("file").asText(), ignored -> new HashMap<>())
                            .put(id, group.getValue());
                }
            }
        }
    }

    private static void refuse(Map<String, Map<String, String>> byFile, ArrayNode problems, String id, String why) {
        byFile.values().forEach(units -> units.remove(id));
        byFile.values().removeIf(Map::isEmpty);
        if (!refused(problems, id)) {
            problems.add(id + ": " + why);
        }
    }

    private static boolean refused(ArrayNode problems, String id) {
        for (JsonNode problem : problems) {
            if (problem.asText().startsWith(id + ": ")) {
                return true;
            }
        }
        return false;
    }
}
