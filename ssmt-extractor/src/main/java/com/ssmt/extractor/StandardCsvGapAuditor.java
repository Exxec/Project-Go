package com.ssmt.extractor;

import com.ssmt.extractor.csv.CsvExtractionSpec;
import com.ssmt.extractor.csv.StandardCsvSchemas;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Read-only review of non-selected columns in recognized standard CSV files.
 *
 * <p>Finding non-ASCII text does not establish that a cell is player-visible or that it is
 * safe to translate. In particular, grouping tags, lookup keys, and mod-specific structured
 * data remain review items. This auditor only makes an otherwise invisible extraction boundary
 * explicit.</p>
 */
public final class StandardCsvGapAuditor {
    private static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;
    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");

    /** A deterministic review finding; neither status approves an automatic schema change. */
    public record Finding(Path relativeSourceFile, String status, String column, String sample) {
        public Finding {
            if (relativeSourceFile == null || status == null || column == null || sample == null) {
                throw new IllegalArgumentException("Standard CSV gap finding values must not be null");
            }
        }
    }

    /**
     * Inspects recognized standard CSVs observed by an extraction run. The source is never
     * modified. Unreadable, oversized, and structurally ambiguous files are reported instead of
     * being silently treated as having no gaps.
     */
    public List<Finding> audit(Path modRoot, ExtractionReport report) {
        Path root = modRoot.toAbsolutePath().normalize();
        List<Finding> findings = new ArrayList<>();
        for (FileCoverage coverage : report.fileCoverage()) {
            Path relative = coverage.sourceFile();
            CsvExtractionSpec spec = StandardCsvSchemas.find(relative).orElse(null);
            if (spec == null) {
                continue;
            }
            inspect(root, relative, spec, findings);
        }
        findings.sort(Comparator.comparing((Finding finding) -> normalized(finding.relativeSourceFile()))
                .thenComparing(Finding::status)
                .thenComparing(Finding::column));
        return List.copyOf(findings);
    }

    private static void inspect(
            Path root, Path relative, CsvExtractionSpec spec, List<Finding> findings) {
        Path file = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !file.startsWith(root)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || hasSymbolicComponent(root, file)) {
            findings.add(unavailable(relative, "UNAVAILABLE_UNSAFE_PATH"));
            return;
        }
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_INPUT_BYTES + 1);
            }
            if (bytes.length > MAX_INPUT_BYTES) {
                findings.add(unavailable(relative, "UNAVAILABLE_INPUT_LIMIT"));
                return;
            }
            inspectText(relative, decode(bytes), spec, findings);
        } catch (IOException exception) {
            findings.add(unavailable(relative, "UNAVAILABLE_READ_OR_ENCODING"));
        }
    }

    private static boolean hasSymbolicComponent(Path root, Path file) {
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) { return true; }
            if (current.equals(root)) { return false; }
        }
        return true;
    }

    private static void inspectText(
            Path relative, String decoded, CsvExtractionSpec spec, List<Finding> findings) {
        String text = decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
        try (CSVParser parser = csvFormat().parse(new StringReader(text))) {
            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty() || hasDuplicateNamedHeader(headers)) {
                findings.add(unavailable(relative, "UNAVAILABLE_AMBIGUOUS_HEADER"));
                return;
            }
            Set<String> selected = new HashSet<>(spec.identityColumns());
            selected.addAll(spec.allTextColumns());
            Set<Integer> reportedColumns = new HashSet<>();
            for (CSVRecord record : parser) {
                for (int index = 0; index < headers.size() && index < record.size(); index++) {
                    String column = headers.get(index);
                    if (column.isBlank() || selected.contains(column)
                            || reportedColumns.contains(index)) {
                        continue;
                    }
                    String value = record.get(index);
                    if (NON_ASCII.matcher(value).find()) {
                        reportedColumns.add(index);
                        findings.add(new Finding(relative,
                                "UNSELECTED_COLUMN_WITH_NON_ASCII_TEXT", column, sample(value)));
                    }
                }
            }
        } catch (IOException | java.io.UncheckedIOException | IllegalArgumentException exception) {
            findings.add(unavailable(relative, "UNAVAILABLE_MALFORMED_CSV"));
        }
    }

    private static Finding unavailable(Path relative, String status) {
        return new Finding(relative, status, "", "");
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        try {
            return decode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException invalidUtf8) {
            return decode(bytes, Charset.forName("GB18030"));
        }
    }

    private static String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static CSVFormat csvFormat() {
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(true).get();
    }

    private static boolean hasDuplicateNamedHeader(List<String> headers) {
        List<String> named = headers.stream().filter(header -> !header.isBlank()).toList();
        return named.size() != new HashSet<>(named).size();
    }

    private static String sample(String value) {
        String compact = value.replace('\n', ' ').replace('\r', ' ').strip();
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }
}
