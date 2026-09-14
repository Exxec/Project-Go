package com.ssmt.patcher;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Token-only JSON edits; no normalization of non-target source text. */
final class JsonTokenPatch {
    private JsonTokenPatch() {
    }

    static String read(Path source) throws IOException {
        String text = decode(Files.readAllBytes(source));
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    static PatchArtifact apply(Path source, Path relative, String text,
            List<TranslationReplacement> replacements, ObjectMapper mapper)
            throws IOException, PatchBuilderException {
        Map<String, TranslationReplacement> pending = new HashMap<>();
        for (TranslationReplacement item : replacements) {
            if (pending.put(item.key(), item) != null) {
                throw new PatchBuilderException("Duplicate JSON replacement: " + item.key());
            }
        }
        StringBuilder output = new StringBuilder();
        int copied = 0;
        try (JsonParser parser = mapper.getFactory().createParser(text)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            int depth = 0;
            while (parser.nextToken() != null) {
                if (parser.currentToken().isStructStart()) {
                    depth++;
                } else if (parser.currentToken().isStructEnd()) {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                boolean fieldName = parser.currentToken() == JsonToken.FIELD_NAME;
                boolean valueString = parser.currentToken() == JsonToken.VALUE_STRING;
                if (!fieldName && !valueString) {
                    continue;
                }
                String key = (fieldName ? "json-key:" : "json:")
                        + parser.getParsingContext().pathAsPointer();
                int start = Math.toIntExact(parser.currentTokenLocation().getCharOffset());
                String value = parser.getText();
                int end = fieldName
                        ? fieldNameEnd(text, start)
                        : Math.toIntExact(parser.currentLocation().getCharOffset());
                TranslationReplacement item = pending.remove(key);
                if (item == null) {
                    continue;
                }
                if (!value.equals(item.originalText()) || start < copied || end > text.length()) {
                    throw new PatchBuilderException("Stale or ambiguous JSON source at " + key);
                }
                output.append(text, copied, start);
                output.append(mapper.writeValueAsString(item.translatedText()));
                copied = end;
            }
        }
        if (!pending.isEmpty()) {
            throw new PatchBuilderException("Missing JSON token: " + pending.keySet());
        }
        output.append(text, copied, text.length());
        return new PatchArtifact(relative, encodeLikeSource(source, text, output.toString()));
    }

    private static int fieldNameEnd(String text, int start) throws PatchBuilderException {
        if (start < 0 || start >= text.length()) {
            throw new PatchBuilderException("Invalid JSON field-name location");
        }
        char first = text.charAt(start);
        if (first != '\'' && first != '"') {
            int end = start;
            while (end < text.length() && text.charAt(end) != ':'
                    && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            return end;
        }
        boolean escaped = false;
        for (int index = start + 1; index < text.length(); index++) {
            char current = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == first) {
                return index + 1;
            }
        }
        throw new PatchBuilderException("Unterminated JSON field name");
    }

    static byte[] encodeLikeSource(Path source, String text, String translated)
            throws IOException, PatchBuilderException {
        byte[] original = Files.readAllBytes(source);
        String decoded = decode(original);
        boolean bom = decoded.startsWith("\uFEFF");
        if (!(bom ? decoded.substring(1) : decoded).equals(text)) {
            throw new PatchBuilderException("Text source changed during injection: " + source.getFileName());
        }
        Charset encoding;
        try {
            strictDecode(original, StandardCharsets.UTF_8);
            encoding = StandardCharsets.UTF_8;
        } catch (CharacterCodingException exception) {
            encoding = Charset.forName("GB18030");
        }
        if (!Arrays.equals(original, encode(decoded, encoding))) {
            throw new PatchBuilderException("Text encoding cannot round-trip source: " + source.getFileName());
        }
        return encode((bom ? "\uFEFF" : "") + translated, encoding);
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        try {
            return strictDecode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException exception) {
            return strictDecode(bytes, Charset.forName("GB18030"));
        }
    }

    private static String strictDecode(byte[] bytes, Charset encoding) throws CharacterCodingException {
        return encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static byte[] encode(String text, Charset encoding) throws CharacterCodingException {
        ByteBuffer bytes = encoding.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        byte[] result = new byte[bytes.remaining()];
        bytes.get(result);
        return result;
    }
}
