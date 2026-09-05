package com.ssmt.extractor;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.extractor.csv.OptInCsvFileSchema;
import com.ssmt.extractor.csv.OptInCsvSchema;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Infers reviewable opt-in CSV schemas from {@link CoverageGapFinding}s, so closing a
 * coverage gap is a human decision over a draft catalog instead of hand-authoring one
 * from scratch. Advisory only: nothing is extracted, translated, approved, or written to
 * a mod here, and suggestions become ordinary opt-in schemas only after a person accepts
 * them through the existing {@code --csv-schema} surface, matching {@code ADR-032}'s
 * evidence-gated coverage policy.
 */
public final class CsvGapSchemaSuggester {

    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");
    private static final char BYTE_ORDER_MARK = '\uFEFF';
    private static final String ID_HEADER = "id";

    /**
     * Suggests one opt-in CSV schema per {@code .csv} finding, in findings order.
     * Non-CSV findings (for example {@code .ship} hull files) are ignored and stay
     * advisory, because CSV identity inference cannot apply to JSON-like sources.
     *
     * @param modRoot mod root the findings were produced against
     * @param findings coverage-gap findings from the same mod
     * @return one suggestion per CSV finding, in findings order
     * @throws SsmtParseException when a candidate file cannot be read or decoded
     */
    public List<GapSchemaSuggestion> suggest(Path modRoot, List<CoverageGapFinding> findings)
            throws SsmtParseException {
        Objects.requireNonNull(modRoot, "modRoot must not be null");
        Objects.requireNonNull(findings, "findings must not be null");
        Path normalizedRoot = modRoot.toAbsolutePath().normalize();
        List<GapSchemaSuggestion> suggestions = new ArrayList<>();
        for (CoverageGapFinding finding : List.copyOf(findings)) {
            Path relative = finding.relativeSourceFile();
            String fileName = String.valueOf(relative.getFileName()).toLowerCase(Locale.ROOT);
            if (!fileName.endsWith(".csv")) {
                continue;
            }
            suggestions.add(suggestOne(normalizedRoot.resolve(relative), relative, finding));
        }
        return List.copyOf(suggestions);
    }

    /**
     * Builds a fresh version-1 catalog holding only suggested entries.
     *
     * @param suggestions suggestions to filter
     * @return validated catalog
     */
    public static OptInCsvSchema toCatalog(List<GapSchemaSuggestion> suggestions) {
        Objects.requireNonNull(suggestions, "suggestions must not be null");
        return new OptInCsvSchema(
                OptInCsvSchema.CURRENT_SCHEMA_VERSION, merged(List.of(), suggestions));
    }

    /**
     * Appends suggested entries to an existing catalog, skipping paths that catalog
     * already contains. Duplicate-path, standard-overlap, and file-count guards are
     * re-validated by {@link OptInCsvSchema}.
     *
     * @param existing catalog to extend
     * @param suggestions suggestions to append
     * @return validated merged catalog
     */
    public static OptInCsvSchema mergeInto(
            OptInCsvSchema existing, List<GapSchemaSuggestion> suggestions) {
        Objects.requireNonNull(existing, "existing must not be null");
        Objects.requireNonNull(suggestions, "suggestions must not be null");
        return new OptInCsvSchema(
                OptInCsvSchema.CURRENT_SCHEMA_VERSION, merged(existing.files(), suggestions));
    }

    private static GapSchemaSuggestion suggestOne(
            Path file, Path relative, CoverageGapFinding finding) throws SsmtParseException {
        String text = decode(file);
        List<String> headers;
        List<List<String>> dataRows;
        try (Reader reader = readerWithoutBom(text);
             CSVParser parser = csvFormat().parse(reader)) {
            headers = parser.getHeaderNames();
            if (hasDuplicateNamedHeader(headers)) {
                return unparseable(relative, "Duplicate CSV header");
            }
            dataRows = dataRows(parser);
        } catch (IOException | IllegalArgumentException exception) {
            return unparseable(relative, "Malformed CSV: " + exception.getMessage());
        }
        int nonAsciiCellCount = countNonAsciiCells(dataRows);
        int identityIndex = identityIndex(headers, dataRows);
        if (identityIndex < 0) {
            return new GapSchemaSuggestion(relative, GapSchemaStatus.NO_ID_COLUMN,
                    Optional.empty(), finding.sample(), nonAsciiCellCount);
        }
        List<String> textColumns = textColumns(headers, dataRows, identityIndex);
        if (textColumns.isEmpty()) {
            return new GapSchemaSuggestion(relative, GapSchemaStatus.NO_TEXT_COLUMNS,
                    Optional.empty(), finding.sample(), nonAsciiCellCount);
        }
        try {
            OptInCsvFileSchema schema = new OptInCsvFileSchema(
                    relative, List.of(headers.get(identityIndex)), textColumns);
            return new GapSchemaSuggestion(relative, GapSchemaStatus.SUGGESTED,
                    Optional.of(schema), finding.sample(), nonAsciiCellCount);
        } catch (IllegalArgumentException exception) {
            return new GapSchemaSuggestion(relative, GapSchemaStatus.UNPARSEABLE,
                    Optional.empty(), String.valueOf(exception.getMessage()), nonAsciiCellCount);
        }
    }

    private static List<OptInCsvFileSchema> merged(
            List<OptInCsvFileSchema> existing, List<GapSchemaSuggestion> suggestions) {
        List<OptInCsvFileSchema> files = new ArrayList<>(existing);
        Set<String> paths = new HashSet<>();
        for (OptInCsvFileSchema file : existing) {
            paths.add(normalize(file.path()));
        }
        for (GapSchemaSuggestion suggestion : suggestions) {
            if (suggestion.status() != GapSchemaStatus.SUGGESTED) {
                continue;
            }
            OptInCsvFileSchema schema = suggestion.schema().orElseThrow();
            if (paths.add(normalize(schema.path()))) {
                files.add(schema);
            }
        }
        return List.copyOf(files);
    }

    private static GapSchemaSuggestion unparseable(Path relative, String reason) {
        return new GapSchemaSuggestion(relative, GapSchemaStatus.UNPARSEABLE,
                Optional.empty(), reason, 0);
    }

    // A header named "id" is preferred, but only when it would also qualify as a
    // fallback candidate: blank or duplicate values disqualify it like any other
    // column, so a broken id header can never yield an unusable schema.
    private static int identityIndex(List<String> headers, List<List<String>> dataRows) {
        int preferred = findIdHeader(headers);
        if (preferred >= 0 && isValidIdentityColumn(dataRows, preferred)) {
            return preferred;
        }
        for (int index = 0; index < headers.size(); index++) {
            if (headers.get(index).isBlank() || index == preferred) {
                continue;
            }
            if (isValidIdentityColumn(dataRows, index)) {
                return index;
            }
        }
        return -1;
    }

    private static int findIdHeader(List<String> headers) {
        for (int index = 0; index < headers.size(); index++) {
            if (headers.get(index).trim().equalsIgnoreCase(ID_HEADER)) {
                return index;
            }
        }
        return -1;
    }

    // Non-blank and pairwise-unique in every data row; vacuously true when the
    // file has no data rows, matching the pre-validation behavior.
    private static boolean isValidIdentityColumn(List<List<String>> dataRows, int index) {
        Set<String> values = new HashSet<>();
        for (List<String> row : dataRows) {
            String value = cell(row, index);
            if (value.isBlank() || !values.add(value)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> textColumns(
            List<String> headers, List<List<String>> dataRows, int identityIndex) {
        List<String> columns = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            if (index == identityIndex || header.isBlank() || columns.contains(header)) {
                continue;
            }
            boolean hasNonAscii = false;
            for (List<String> row : dataRows) {
                if (NON_ASCII.matcher(cell(row, index)).find()) {
                    hasNonAscii = true;
                    break;
                }
            }
            if (hasNonAscii) {
                columns.add(header);
            }
        }
        return List.copyOf(columns);
    }

    private static int countNonAsciiCells(List<List<String>> dataRows) {
        int count = 0;
        for (List<String> row : dataRows) {
            for (String value : row) {
                if (NON_ASCII.matcher(value).find()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private static List<List<String>> dataRows(CSVParser parser) {
        List<List<String>> rows = new ArrayList<>();
        for (CSVRecord record : parser) {
            List<String> cells = new ArrayList<>();
            for (int index = 0; index < record.size(); index++) {
                cells.add(record.get(index));
            }
            if (cells.isEmpty()
                    || cells.getFirst().stripLeading().startsWith("#")
                    || cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(List.copyOf(cells));
        }
        return List.copyOf(rows);
    }

    private static boolean hasDuplicateNamedHeader(List<String> headers) {
        List<String> named = headers.stream().filter(header -> !header.isBlank()).toList();
        return named.size() != new HashSet<>(named).size();
    }

    private static CSVFormat csvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(true)
                .get();
    }

    private static Reader readerWithoutBom(String decoded) throws IOException {
        PushbackReader reader = new PushbackReader(new StringReader(decoded), 1);
        int first = reader.read();
        if (first != -1 && first != BYTE_ORDER_MARK) {
            reader.unread(first);
        }
        return reader;
    }

    private static String decode(Path file) throws SsmtParseException {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return decodeStrict(bytes, StandardCharsets.UTF_8);
            } catch (CharacterCodingException invalidUtf8) {
                return decodeStrict(bytes, Charset.forName("GB18030"));
            }
        } catch (IOException exception) {
            throw new SsmtParseException("Could not read candidate file", file, exception);
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String normalize(Path path) {
        return path.normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
