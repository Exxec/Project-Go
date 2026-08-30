package com.ssmt.patcher;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reinjects stable-key translations into standard CSV and JSON-like files.
 */
public final class StandardFileInjector {
    private static final Pattern CSV_KEY = Pattern.compile("^csv:([^=]+)=([^:]+):(.+)$");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    /**
     * Builds one complete translated artifact.
     *
     * @param sourceRoot source mod root
     * @param replacements replacements for exactly one file
     * @return complete artifact
     * @throws PatchBuilderException on stale or malformed source data
     */
    public PatchArtifact inject(Path sourceRoot, List<TranslationReplacement> replacements)
            throws PatchBuilderException {
        List<TranslationReplacement> copy = List.copyOf(replacements);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("At least one replacement is required");
        }
        Path relative = copy.getFirst().sourceFile();
        if (copy.stream().anyMatch(item -> !relative.equals(item.sourceFile()))) {
            throw new IllegalArgumentException("All replacements must target one file");
        }
        Path root = sourceRoot.toAbsolutePath().normalize();
        Path source = root.resolve(relative).normalize();
        if (!source.startsWith(root)) {
            throw new IllegalArgumentException("Source file escapes source root");
        }
        String name = relative.toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return injectCsv(source, relative, copy);
        }
        if (name.endsWith(".json") || name.endsWith(".faction") || name.endsWith(".variant")) {
            return injectJson(source, relative, copy);
        }
        throw new PatchBuilderException("Unsupported reinjection format: " + relative);
    }

    private static PatchArtifact injectJson(
            Path source,
            Path relative,
            List<TranslationReplacement> replacements) throws PatchBuilderException {
        try {
            JsonNode root = JSON.readTree(source.toFile());
            for (TranslationReplacement replacement : replacements) {
                if (!replacement.key().startsWith("json:/")) {
                    throw new PatchBuilderException("Invalid JSON key " + replacement.key());
                }
                replaceJson(root, replacement);
            }
            return new PatchArtifact(relative, JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(root));
        } catch (IOException | IllegalArgumentException exception) {
            throw new PatchBuilderException(
                    "Could not inject JSON file " + relative + ": " + exception.getMessage(),
                    exception);
        }
    }

    private static void replaceJson(JsonNode root, TranslationReplacement replacement)
            throws PatchBuilderException {
        String[] encoded = replacement.key().substring("json:/".length()).split("/", -1);
        JsonNode parent = root;
        for (int index = 0; index < encoded.length - 1; index++) {
            parent = child(parent, decodePointer(encoded[index]));
            if (parent == null) {
                throw new PatchBuilderException("Missing JSON path " + replacement.key());
            }
        }
        String leaf = decodePointer(encoded[encoded.length - 1]);
        JsonNode current = child(parent, leaf);
        if (current == null || !current.isTextual()
                || !current.textValue().equals(replacement.originalText())) {
            throw new PatchBuilderException("Stale JSON source text at " + replacement.key());
        }
        if (parent instanceof ObjectNode object) {
            object.put(leaf, replacement.translatedText());
        } else if (parent instanceof ArrayNode array) {
            array.set(parseIndex(leaf), JSON.getNodeFactory().textNode(replacement.translatedText()));
        } else {
            throw new PatchBuilderException("JSON parent is not a container at " + replacement.key());
        }
    }

    private static JsonNode child(JsonNode parent, String token) throws PatchBuilderException {
        if (parent instanceof ObjectNode) {
            return parent.get(token);
        }
        if (parent instanceof ArrayNode) {
            int index = parseIndex(token);
            return index < parent.size() ? parent.get(index) : null;
        }
        return null;
    }

    private static int parseIndex(String token) throws PatchBuilderException {
        try {
            int index = Integer.parseInt(token);
            if (index < 0) {
                throw new NumberFormatException("negative");
            }
            return index;
        } catch (NumberFormatException exception) {
            throw new PatchBuilderException("Invalid JSON array index " + token, exception);
        }
    }

    private static String decodePointer(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * One parsed CSV data row, retaining its original raw source text so that
     * unchanged Starsector-significant structural rows (blank sentinel rows and
     * {@code #}-prefixed comment/sentinel rows) can be re-emitted byte-identical
     * instead of passing through generic per-cell quoting.
     */
    private record Row(Map<String, String> original, Map<String, String> current,
            String rawText, boolean structural) {
    }

    private static PatchArtifact injectCsv(
            Path source,
            Path relative,
            List<TranslationReplacement> replacements) throws PatchBuilderException {
        CSVFormat inputFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(true)
                .get();
        try {
            String sourceText = decodeCsvSource(source);
            List<String> headers;
            List<CSVRecord> records;
            try (CSVParser parser = inputFormat.parse(new StringReader(sourceText))) {
                headers = parser.getHeaderNames();
                records = parser.getRecords();
            }
            List<Row> rows = new ArrayList<>();
            for (int index = 0; index < records.size(); index++) {
                CSVRecord record = records.get(index);
                Map<String, String> original = new LinkedHashMap<>();
                for (String header : headers) {
                    original.put(header, record.get(header));
                }
                long start = record.getCharacterPosition();
                long end = index + 1 < records.size()
                        ? records.get(index + 1).getCharacterPosition()
                        : sourceText.length();
                String rawText = stripTrailingLineSeparator(
                        sourceText.substring((int) start, (int) end));
                rows.add(new Row(original, new LinkedHashMap<>(original), rawText, isStructuralRow(record)));
            }
            for (TranslationReplacement replacement : replacements) {
                replaceCsv(rows, replacement);
            }
            StringWriter writer = new StringWriter();
            writeCsvRecord(writer, headers.toArray());
            for (Row row : rows) {
                if (row.structural() && row.current().equals(row.original())) {
                    writer.write(row.rawText());
                    writer.write(CSVFormat.DEFAULT.getRecordSeparator());
                } else {
                    writeCsvRecord(writer, headers.stream().map(row.current()::get).toArray());
                }
            }
            return PatchArtifact.utf8(relative, writer.toString());
        } catch (IOException | IllegalArgumentException exception) {
            throw new PatchBuilderException(
                    "Could not inject CSV file " + relative + ": " + exception.getMessage(),
                    exception);
        }
    }

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    private static String decodeCsvSource(Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        String decoded;
        try {
            decoded = decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException invalidUtf8) {
            decoded = decodeStrict(bytes, Charset.forName("GB18030"));
        }
        return !decoded.isEmpty() && decoded.charAt(0) == BYTE_ORDER_MARK
                ? decoded.substring(1)
                : decoded;
    }

    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static boolean isStructuralRow(CSVRecord record) {
        return isBlankRow(record) || isHashPrefixedRow(record);
    }

    private static boolean isBlankRow(CSVRecord record) {
        if (record.size() == 0) {
            return false;
        }
        for (int index = 0; index < record.size(); index++) {
            if (!record.get(index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHashPrefixedRow(CSVRecord record) {
        return record.size() > 0 && record.get(0).stripLeading().startsWith("#");
    }

    private static String stripTrailingLineSeparator(String text) {
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n") || text.endsWith("\r")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static void replaceCsv(
            List<Row> rows,
            TranslationReplacement replacement) throws PatchBuilderException {
        Matcher matcher = CSV_KEY.matcher(replacement.key());
        if (!matcher.matches()) {
            throw new PatchBuilderException("Invalid CSV key " + replacement.key());
        }
        String[] identityColumns = decodeCsv(matcher.group(1)).split(",", -1);
        String[] identityValues = decodeCsv(matcher.group(2)).split("\0", -1);
        if (identityColumns.length != identityValues.length) {
            throw new PatchBuilderException("Invalid composite CSV identity " + replacement.key());
        }
        String textColumn = decodeCsv(matcher.group(3));
        for (Row row : rows) {
            boolean identityMatches = true;
            for (int index = 0; index < identityColumns.length; index++) {
                identityMatches &= identityValues[index].equals(row.current().get(identityColumns[index]));
            }
            if (identityMatches) {
                if (!replacement.originalText().equals(row.current().get(textColumn))) {
                    throw new PatchBuilderException("Stale CSV source text at " + replacement.key());
                }
                row.current().put(textColumn, replacement.translatedText());
                return;
            }
        }
        throw new PatchBuilderException("Missing CSV identity at " + replacement.key());
    }

    private static String decodeCsv(String value) {
        return value.replace("%00", "\0")
                .replace("%2C", ",")
                .replace("%3D", "=")
                .replace("%3A", ":")
                .replace("%25", "%");
    }

    private static void writeCsvRecord(StringWriter writer, Object... values) {
        writer.write(CSVFormat.DEFAULT.format(values));
        writer.write(CSVFormat.DEFAULT.getRecordSeparator());
    }
}
