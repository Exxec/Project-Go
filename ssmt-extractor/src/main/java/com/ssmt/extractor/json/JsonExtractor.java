package com.ssmt.extractor.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts textual JSON nodes selected by an explicit specification.
 */
public final class JsonExtractor implements FileExtractor {

    private final JsonExtractionSpec spec;
    private final JsonMapper mapper;

    public JsonExtractor(JsonExtractionSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();
    }

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Path name = sourceFile.getFileName();
        if (name == null) {
            return false;
        }
        String lowerName = name.toString().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".json")
                || lowerName.endsWith(".faction")
                || lowerName.endsWith(".variant");
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        Objects.requireNonNull(request, "request must not be null");
        if (!supports(request.sourceFile())) {
            throw new SsmtParseException("Unsupported JSON-like file", request.sourceFile());
        }
        if (!Files.isRegularFile(request.sourceFile())) {
            throw new SsmtParseException("Missing JSON-like file", request.sourceFile());
        }

        JsonNode root;
        try {
            String source = Files.readString(request.sourceFile(), StandardCharsets.UTF_8);
            int probableTypoLine = probableLiteralTypoLine(source, "Ture");
            if (probableTypoLine > 0) {
                throw new SsmtParseException(
                        "Malformed JSON: probable literal typo 'Ture'; did you mean 'true'?",
                        request.sourceFile(),
                        probableTypoLine,
                        null,
                        "JSON_PROBABLE_LITERAL_TYPO",
                        "Replace 'Ture' with 'true' in the source file, then retry.");
            }
            root = mapper.readTree(normalizeUppercaseLiterals(source));
        } catch (SsmtParseException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            int line = exception.getLocation() == null
                    ? -1
                    : Math.toIntExact(exception.getLocation().getLineNr());
            throw new SsmtParseException(
                    "Malformed JSON: " + exception.getOriginalMessage(),
                    request.sourceFile(),
                    line,
                    exception);
        } catch (IOException exception) {
            throw new SsmtParseException(
                    "Could not read JSON-like file", request.sourceFile(), exception);
        }

        List<ExtractedString> extracted = new ArrayList<>();
        if (spec.extractsAllTextLeaves()) {
            collectTextLeaves(root, "", request, extracted);
        } else {
            collectSelectedPointers(root, request, extracted);
            if (!spec.patterns().isEmpty()) {
                collectPatternMatches(root, request, extracted);
            }
        }
        extracted.sort(Comparator.comparing(ExtractedString::key));
        return List.copyOf(extracted);
    }

    private static String normalizeUppercaseLiterals(String source) {
        StringBuilder normalized = new StringBuilder(source.length());
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < source.length();) {
            char current = source.charAt(index);
            if (quote != 0) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                index++;
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                normalized.append(current);
                index++;
                continue;
            }
            String replacement = uppercaseLiteralAt(source, index);
            if (replacement != null) {
                normalized.append(replacement);
                index += replacement.length();
            } else {
                normalized.append(current);
                index++;
            }
        }
        return normalized.toString();
    }

    private static int probableLiteralTypoLine(String source, String token) {
        char quote = 0;
        boolean escaped = false;
        int line = 1;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '\n') {
                line++;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                continue;
            }
            int end = index + token.length();
            if (end <= source.length()
                    && source.regionMatches(index, token, 0, token.length())
                    && (index == 0 || !isIdentifierCharacter(source.charAt(index - 1)))
                    && (end == source.length() || !isIdentifierCharacter(source.charAt(end)))) {
                return line;
            }
        }
        return -1;
    }

    private static String uppercaseLiteralAt(String source, int index) {
        for (String literal : List.of("TRUE", "FALSE", "NULL")) {
            int end = index + literal.length();
            if (end <= source.length()
                    && source.regionMatches(index, literal, 0, literal.length())
                    && (index == 0 || !isIdentifierCharacter(source.charAt(index - 1)))
                    && (end == source.length() || !isIdentifierCharacter(source.charAt(end)))) {
                return literal.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static boolean isIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private void collectSelectedPointers(
            JsonNode root,
            ExtractionRequest request,
            List<ExtractedString> extracted
    ) throws SsmtParseException {
        for (String pointer : spec.pointers()) {
            JsonNode node = root.at(pointer);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (!node.isTextual()) {
                throw new SsmtParseException(
                        "Selected JSON pointer is not textual: " + pointer,
                        request.sourceFile());
            }
            addText(pointer, node.textValue(), request, extracted);
        }
    }

    private void collectPatternMatches(
            JsonNode root,
            ExtractionRequest request,
            List<ExtractedString> extracted
    ) throws SsmtParseException {
        for (String pattern : spec.patterns()) {
            String[] tokens = pattern.substring(1).split("/", -1);
            matchPattern(root, tokens, 0, "", request, extracted);
        }
    }

    private void matchPattern(
            JsonNode node,
            String[] tokens,
            int index,
            String pointer,
            ExtractionRequest request,
            List<ExtractedString> extracted
    ) throws SsmtParseException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (index == tokens.length) {
            if (!node.isTextual()) {
                throw new SsmtParseException(
                        "Selected JSON pointer is not textual: " + pointer,
                        request.sourceFile());
            }
            addText(pointer, node.textValue(), request, extracted);
            return;
        }
        String token = tokens[index];
        if ("*".equals(token)) {
            if (!node.isObject()) {
                return;
            }
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            for (String field : fields) {
                matchPattern(
                        node.get(field),
                        tokens,
                        index + 1,
                        pointer + "/" + escapePointerToken(field),
                        request,
                        extracted);
            }
        } else {
            matchPattern(
                    child(node, token),
                    tokens,
                    index + 1,
                    pointer + "/" + token,
                    request,
                    extracted);
        }
    }

    private static JsonNode child(JsonNode node, String token) {
        return node.isObject() ? node.get(unescapePointerToken(token)) : null;
    }

    private static String unescapePointerToken(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private void collectTextLeaves(
            JsonNode node,
            String pointer,
            ExtractionRequest request,
            List<ExtractedString> extracted
    ) {
        if (node.isTextual()) {
            addText(pointer, node.textValue(), request, extracted);
        } else if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, JsonNode> field : fields) {
                collectTextLeaves(
                        field.getValue(),
                        pointer + "/" + escapePointerToken(field.getKey()),
                        request,
                        extracted);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collectTextLeaves(
                        node.get(index),
                        pointer + "/" + index,
                        request,
                        extracted);
            }
        }
    }

    private static void addText(
            String pointer,
            String text,
            ExtractionRequest request,
            List<ExtractedString> extracted
    ) {
        if (!text.isEmpty()) {
            extracted.add(new ExtractedString(
                    request.modId(),
                    request.relativeSourceFile(),
                    "json:" + pointer,
                    text,
                    -1));
        }
    }

    private static String escapePointerToken(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
