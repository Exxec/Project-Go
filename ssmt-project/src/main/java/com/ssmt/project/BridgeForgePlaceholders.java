package com.ssmt.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** BridgeForge placeholder semantics, including argument-aware format-slot correction. */
final class BridgeForgePlaceholders {
    private static final Pattern VARIABLE = Pattern.compile("\\$[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*|\\x01");
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([sdfxXeEgGcbhno%])");
    private record Slot(int argument, String conversion, int start, int end, String token, boolean explicit) { }

    static Map<String, Integer> tokens(String text) {
        Map<String, Integer> result = new HashMap<>();
        var matcher = Pattern.compile(FORMAT.pattern() + "|" + VARIABLE.pattern()).matcher(text);
        while (matcher.find()) {
            if (matcher.group(2) == null) {
                result.merge(matcher.group(), 1, Integer::sum);
            }
        }
        for (Slot slot : slots(text)) {
            result.merge("%" + slot.argument() + slot.conversion(), 1, Integer::sum);
        }
        return result;
    }

    private static List<Slot> slots(String text) {
        List<Slot> result = new ArrayList<>();
        int ordinary = 0;
        var matcher = FORMAT.matcher(text);
        while (matcher.find()) {
            if (matcher.group(2).equals("%")) {
                continue;
            }
            boolean explicit = matcher.group(1) != null;
            int argument = explicit ? Integer.parseInt(matcher.group(1)) : ++ordinary;
            result.add(new Slot(argument, matcher.group(2), matcher.start(), matcher.end(), matcher.group(), explicit));
        }
        return result;
    }

    static String resolve(String source, String target) {
        if (tokens(source).equals(tokens(target))) {
            return target;
        }
        List<Slot> original = slots(source);
        List<Slot> translated = slots(target);
        if (original.isEmpty() || original.size() != translated.size()
                || translated.stream().anyMatch(Slot::explicit)) {
            return null;
        }
        Set<Integer> used = new HashSet<>();
        StringBuilder result = new StringBuilder();
        int last = 0;
        for (Slot slot : translated) {
            Slot match = original.stream().filter(candidate -> candidate.conversion().equals(slot.conversion())
                    && !used.contains(candidate.argument())).findFirst().orElse(null);
            if (match == null) {
                return null;
            }
            used.add(match.argument());
            result.append(target, last, slot.start()).append('%').append(match.argument())
                    .append('$').append(slot.token().substring(1));
            last = slot.end();
        }
        result.append(target.substring(last));
        return tokens(source).equals(tokens(result.toString())) ? result.toString() : null;
    }
}
