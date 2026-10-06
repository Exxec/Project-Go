package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Raw-span discovery matching BridgeForge's CSV, JSON-like and loose-Java units. */
final class BridgeForgeTextUnits {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern TOKEN = Pattern.compile(
            "(?<ws>\\s+)|(?<comment>#[^\\n]*|//[^\\n]*)|(?<string>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
            + "|(?<punct>[{}\\[\\]:,])|(?<word>[^\\s{}\\[\\]:,\"'#]+)");
    private static final Pattern JAVA_STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"");

    record Unit(String id, String kind, String source, ObjectNode context,
            int start, int end, boolean quoted) { }
    record Cell(int row, int column, int start, int end, String value, boolean quoted) { }

    static boolean cjk(String text) {
        return text.codePoints().anyMatch(value -> value >= 0x3400 && value <= 0x4dbf
                || value >= 0x4e00 && value <= 0x9fff || value >= 0xf900 && value <= 0xfaff);
    }

    static List<Cell> cells(String text) {
        List<Cell> result = new ArrayList<>();
        int index = 0;
        int row = 0;
        int column = 0;
        while (true) {
            int start = index;
            boolean quoted = index < text.length() && text.charAt(index) == '"';
            StringBuilder value = new StringBuilder();
            if (quoted) {
                index++;
                while (index < text.length()) {
                    char ch = text.charAt(index++);
                    if (ch == '"') {
                        if (index < text.length() && text.charAt(index) == '"') {
                            value.append('"');
                            index++;
                            continue;
                        }
                        break;
                    }
                    value.append(ch);
                }
            }
            while (index < text.length() && ",\r\n".indexOf(text.charAt(index)) < 0) {
                value.append(text.charAt(index++));
            }
            result.add(new Cell(row, column, start, index, value.toString(), quoted));
            if (index >= text.length()) {
                return result;
            }
            if (text.charAt(index) == ',') {
                column++;
                index++;
            } else {
                index += text.startsWith("\r\n", index) ? 2 : 1;
                row++;
                column = 0;
                if (index >= text.length()) {
                    return result;
                }
            }
        }
    }

    static List<Unit> csv(String file, String text) {
        List<Cell> cells = cells(text);
        List<String> header = cells.stream().filter(cell -> cell.row() == 0)
                .map(cell -> cell.value().strip()).toList();
        int idColumn = Math.max(0, header.indexOf("id"));
        int typeColumn = header.indexOf("type");
        Map<Integer, Map<Integer, String>> rows = new LinkedHashMap<>();
        for (Cell cell : cells) {
            rows.computeIfAbsent(cell.row(), ignored -> new HashMap<>()).put(cell.column(), cell.value());
        }
        Map<String, Integer> seen = new HashMap<>();
        Map<Integer, String> keys = new HashMap<>();
        for (var row : rows.entrySet()) {
            if (row.getKey() == 0) {
                continue;
            }
            String key = row.getValue().getOrDefault(idColumn, "").strip();
            if (key.isEmpty()) {
                key = "row" + row.getKey();
            }
            String type = row.getValue().getOrDefault(typeColumn, "").strip();
            if (!type.isEmpty()) {
                key += "/" + type;
            }
            int occurrence = seen.merge(key, 1, Integer::sum);
            keys.put(row.getKey(), key + (occurrence == 1 ? "" : "~" + occurrence));
        }
        List<Unit> result = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.row() == 0 || rows.get(cell.row()).getOrDefault(0, "").stripLeading().startsWith("#")
                    || !cjk(cell.value())) {
                continue;
            }
            String column = cell.column() < header.size() ? header.get(cell.column()) : "#" + cell.column();
            String key = keys.get(cell.row());
            ObjectNode context = JSON.createObjectNode().put("row", key).put("column", column);
            result.add(new Unit("csv:" + file + "#" + key + ":" + column, "csv", cell.value(),
                    context, cell.start(), cell.end(), cell.quoted()));
        }
        return result;
    }

    private static final class Frame {
        private final boolean object;
        private Object key;
        private int index;
        private boolean expectKey;
        Frame(boolean object) {
            this.object = object;
            expectKey = object;
        }
        Object current() {
            return object ? key : index;
        }
    }

    static List<Unit> json(String file, String text) {
        List<Unit> result = new ArrayList<>();
        List<Frame> stack = new ArrayList<>();
        List<Object> path = new ArrayList<>();
        int ordinal = 0;
        var matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            if (matcher.group("ws") != null || matcher.group("comment") != null) {
                continue;
            }
            String token = matcher.group();
            Frame frame = stack.isEmpty() ? null : stack.getLast();
            if (matcher.group("punct") != null) {
                switch (token) {
                    case "{", "[" -> {
                        if (frame != null) {
                            path.add(frame.current());
                        }
                        stack.add(new Frame(token.equals("{")));
                    }
                    case "}", "]" -> {
                        if (!stack.isEmpty()) {
                            stack.removeLast();
                        }
                        if (!stack.isEmpty() && !path.isEmpty()) {
                            path.removeLast();
                        }
                    }
                    case ":" -> {
                        if (frame != null) {
                            frame.expectKey = false;
                        }
                    }
                    case "," -> {
                        if (frame != null && frame.object) {
                            frame.expectKey = true;
                        } else if (frame != null) {
                            frame.index++;
                        }
                    }
                    default -> { /* tokenizer limits punctuation */ }
                }
                continue;
            }
            boolean string = matcher.group("string") != null;
            String value = string ? decodeJson(token) : token;
            boolean key = frame != null && frame.object && frame.expectKey;
            if (key) {
                frame.key = value;
            }
            if (string) {
                List<Object> here = new ArrayList<>(path);
                if (frame != null) {
                    here.add(key ? value : frame.current());
                }
                ObjectNode context = JSON.createObjectNode();
                context.set("path", JSON.valueToTree(here));
                // Include non-CJK keys for duplicate-sibling checks during application.
                result.add(new Unit("json:" + file + "@" + ordinal,
                        key ? "json-key" : "json", value, context, matcher.start(), matcher.end(), true));
                if (cjk(value)) {
                    ordinal++;
                }
            }
        }
        return result;
    }

    static String decodeJson(String token) {
        String normalized = token.startsWith("'")
                ? "\"" + token.substring(1, token.length() - 1).replace("\\'", "'").replace("\"", "\\\"") + "\""
                : token;
        try {
            return JSON.readValue(normalized, String.class);
        } catch (java.io.IOException exception) {
            return token.substring(1, token.length() - 1);
        }
    }

    static List<Unit> java(String file, String text) {
        String blanked = blankComments(text);
        List<Unit> result = new ArrayList<>();
        var matcher = JAVA_STRING.matcher(blanked);
        while (matcher.find()) {
            String value = decodeJava(matcher.group());
            if (cjk(value)) {
                int line = 1 + (int) blanked.substring(0, matcher.start()).chars().filter(ch -> ch == '\n').count();
                result.add(new Unit("java:" + file + "@" + result.size(), "java", value,
                        JSON.createObjectNode().put("line", line), matcher.start(), matcher.end(), true));
            }
        }
        return result;
    }

    static String blankComments(String text) {
        char[] result = text.toCharArray();
        int index = 0;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == '"' || ch == '\'') {
                char quote = ch;
                index++;
                while (index < text.length()) {
                    char current = text.charAt(index++);
                    if (current == '\\' && index < text.length()) {
                        index++;
                    } else if (current == quote) {
                        break;
                    }
                }
            } else if (text.startsWith("//", index) || text.startsWith("/*", index)) {
                boolean block = text.startsWith("/*", index);
                int end = block ? text.indexOf("*/", index + 2) : text.indexOf('\n', index + 2);
                end = end < 0 ? text.length() : block ? end + 2 : end;
                while (index < end) {
                    if (result[index] != '\n' && result[index] != '\r') {
                        result[index] = ' ';
                    }
                    index++;
                }
            } else {
                index++;
            }
        }
        return new String(result);
    }

    static String decodeJava(String token) {
        StringBuilder result = new StringBuilder();
        for (int index = 1; index < token.length() - 1; index++) {
            char ch = token.charAt(index);
            if (ch != '\\') {
                result.append(ch);
                continue;
            }
            char escape = token.charAt(++index);
            if (escape == 'u' && index + 4 < token.length() - 1
                    && token.substring(index + 1, index + 5).matches("[a-fA-F0-9]{4}")) {
                result.append((char) Integer.parseInt(token.substring(index + 1, index + 5), 16));
                index += 4;
            } else {
                result.append(switch (escape) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case '0' -> '\0';
                    default -> escape;
                });
            }
        }
        return result.toString();
    }
}
