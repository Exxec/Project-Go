package com.ssmt.extractor;

import com.ssmt.extractor.csv.StandardCsvSchemas;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
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
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.csv.CSVFormat;

/** Bounded read-only structural review; no schema or text visibility is inferred. */
public final class CsvStructureAuditor {
    /** Maximum bytes reviewed per CSV, whether from a directory or an archive entry. */
    public static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ROWS = 100_000;

    /** Record numbers are CSV records, not physical lines with multiline quoting. */
    public record Finding(String path, long record, String code, String disposition) { }

    /** Audits explicitly listed mod-relative CSV paths without changing their bytes. */
    public List<Finding> audit(Path modRoot, List<Path> files) {
        Path requestedRoot = modRoot.toAbsolutePath().normalize();
        List<Finding> findings = new ArrayList<>();
        for (Path relative : files.stream().sorted(Comparator.comparing(Path::toString)).toList()) {
            String path = relative.toString().replace('\\', '/');
            if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) { continue; }
            try {
                rejectSymbolicComponents(requestedRoot);
                if (!Files.isDirectory(requestedRoot, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("CSV root is not a directory");
                }
                Path root = requestedRoot.toRealPath();
                Path file = root.resolve(relative).normalize();
                if (relative.isAbsolute() || !file.startsWith(root)
                        || !file.toRealPath().equals(file)) {
                    throw new IOException("Unsafe CSV path");
                }
                rejectSymbolicComponents(file);
                byte[] bytes;
                try (var input = Files.newInputStream(file)) {
                    bytes = input.readNBytes(MAX_INPUT_BYTES + 1);
                }
                inspectBytes(relative, bytes, findings);
            } catch (IOException exception) {
                add(findings, path, -1, "READ_OR_ENCODING_FAILED");
            }
        }
        return List.copyOf(findings);
    }

    private static void rejectSymbolicComponents(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) { throw new IOException("Linked CSV path"); }
        }
    }

    /** Reviews already-bounded, archive-relative CSV payloads without filesystem access. */
    public List<Finding> auditBytes(Map<Path, byte[]> entries) {
        List<Finding> findings = new ArrayList<>();
        for (var entry : new TreeMap<>(entries).entrySet()) {
            Path relative = entry.getKey();
            String path = relative.toString().replace('\\', '/');
            if (relative.isAbsolute() || relative.normalize().startsWith("..")
                    || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
                continue;
            }
            inspectBytes(relative, entry.getValue(), findings);
        }
        return List.copyOf(findings);
    }

    private static void inspectBytes(Path relative, byte[] bytes, List<Finding> findings) {
        String path = relative.toString().replace('\\', '/');
        if (bytes.length > MAX_INPUT_BYTES) {
            add(findings, path, -1, "INPUT_LIMIT_EXCEEDED");
            return;
        }
        try {
            String text;
            try { text = decode(bytes, StandardCharsets.UTF_8); }
            catch (java.nio.charset.CharacterCodingException exception) {
                text = decode(bytes, Charset.forName("GB18030"));
            }
            if (text.startsWith("\uFEFF")) { text = text.substring(1); }
            inspect(relative, path, text, findings);
        } catch (java.nio.charset.CharacterCodingException exception) {
            add(findings, path, -1, "READ_OR_ENCODING_FAILED");
        }
    }

    private static void inspect(Path relative, String path, String text, List<Finding> findings) {
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(text))) {
            var rows = parser.iterator();
            if (!rows.hasNext()) {
                add(findings, path, -1, "MISSING_HEADER");
                return;
            }
            var header = rows.next();
            var names = header.toList();
            var seenHeaders = new HashSet<String>();
            for (String name : names) {
                if (!name.isBlank() && !seenHeaders.add(name)) {
                    add(findings, path, 1, "DUPLICATE_HEADER");
                }
            }
            var schema = StandardCsvSchemas.find(relative);
            List<String> identityColumns = schema.map(value -> value.identityColumns()).orElse(List.of());
            boolean identityAvailable = !identityColumns.isEmpty() && names.containsAll(identityColumns);
            if (!identityAvailable) { add(findings, path, 1, "IDENTITY_NOT_ASSESSED"); }
            var identities = new HashSet<List<String>>();
            int count = 0;
            while (rows.hasNext()) {
                var row = rows.next();
                if (++count > MAX_ROWS) {
                    add(findings, path, row.getRecordNumber(), "ROW_LIMIT_EXCEEDED");
                    break;
                }
                if (row.size() > names.size()) { add(findings, path, row.getRecordNumber(), "EXTRA_COLUMNS"); }
                if (row.size() < names.size()) { add(findings, path, row.getRecordNumber(), "SHORT_ROW"); }
                if (identityAvailable) {
                    var identity = new ArrayList<String>();
                    for (String column : identityColumns) {
                        int index = names.indexOf(column);
                        identity.add(index < row.size() ? row.get(index) : "");
                    }
                    if (identity.stream().anyMatch(String::isBlank)) {
                        add(findings, path, row.getRecordNumber(), "BLANK_IDENTITY");
                    } else if (!identities.add(List.copyOf(identity))) {
                        add(findings, path, row.getRecordNumber(), "DUPLICATE_IDENTITY");
                    }
                }
            }
        } catch (IOException | java.io.UncheckedIOException | IllegalArgumentException exception) {
            add(findings, path, -1, "MALFORMED_CSV");
        }
    }

    private static String decode(byte[] bytes, Charset charset)
            throws java.nio.charset.CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void add(List<Finding> findings, String path, long record, String code) {
        findings.add(new Finding(path, record, code, "REVIEW"));
    }
}
