package com.ssmt.extractor.csv;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Extracts explicitly configured cells using Apache Commons CSV.
 */
public final class CsvExtractor implements FileExtractor {

    private static final char BYTE_ORDER_MARK = '\uFEFF';
    private final CsvExtractionSpec spec;

    public CsvExtractor(CsvExtractionSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
    }

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Path fileName = sourceFile.getFileName();
        return fileName != null
                && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        Objects.requireNonNull(request, "request must not be null");
        if (!supports(request.sourceFile())) {
            throw new SsmtParseException("Unsupported file type", request.sourceFile());
        }
        if (!Files.isRegularFile(request.sourceFile())) {
            throw new SsmtParseException("Missing CSV file", request.sourceFile());
        }

        try (Reader reader = utf8ReaderWithoutBom(request.sourceFile());
             CSVParser parser = csvFormat().parse(reader)) {
            validateHeaders(parser, request.sourceFile());
            return extractRecords(parser, request);
        } catch (SsmtParseException exception) {
            throw exception;
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            throw new SsmtParseException(
                    "Malformed CSV: " + exception.getMessage(),
                    request.sourceFile(),
                    exception);
        }
    }

    private static CSVFormat csvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(true)
                .get();
    }

    private static Reader utf8ReaderWithoutBom(Path sourceFile) throws IOException {
        byte[] bytes = Files.readAllBytes(sourceFile);
        String decoded;
        try {
            decoded = decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException invalidUtf8) {
            decoded = decodeStrict(bytes, Charset.forName("GB18030"));
        }
        PushbackReader reader = new PushbackReader(new StringReader(decoded), 1);
        int first = reader.read();
        if (first != -1 && first != BYTE_ORDER_MARK) {
            reader.unread(first);
        }
        return reader;
    }

    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private void validateHeaders(CSVParser parser, Path sourceFile)
            throws SsmtParseException {
        List<String> headers = parser.getHeaderNames();
        List<String> namedHeaders = headers.stream()
                .filter(header -> !header.isBlank())
                .toList();
        if (namedHeaders.size() != new HashSet<>(namedHeaders).size()) {
            throw new SsmtParseException("Duplicate CSV header", sourceFile);
        }
        for (String identityColumn : spec.identityColumns()) {
            requireHeader(headers, identityColumn, sourceFile);
        }
        for (String column : spec.textColumns()) {
            requireHeader(headers, column, sourceFile);
        }
    }

    private static void requireHeader(List<String> headers, String column, Path sourceFile)
            throws SsmtParseException {
        if (!headers.contains(column)) {
            throw new SsmtParseException(
                    "Missing configured CSV column: " + column, sourceFile);
        }
    }

    private List<ExtractedString> extractRecords(
            CSVParser parser,
            ExtractionRequest request
    ) throws SsmtParseException {
        Set<String> identities = new HashSet<>();
        List<ExtractedString> extracted = new ArrayList<>();
        List<String> presentTextColumns = presentTextColumns(parser);
        for (CSVRecord record : parser) {
            if (record.size() > 0 && record.get(0).stripLeading().startsWith("#")) {
                continue;
            }
            List<String> identityValues = spec.identityColumns().stream()
                    .map(record::get)
                    .toList();
            String identity = String.join("\0", identityValues);
            if (identityValues.stream().anyMatch(String::isBlank)) {
                boolean localizableCellsAreEmpty = presentTextColumns.stream()
                        .map(record::get)
                        .allMatch(String::isBlank);
                boolean labeledSentinelRow = !record.get(0).isBlank()
                        && !parser.getHeaderNames().get(0).equals(spec.identityColumn());
                if (localizableCellsAreEmpty
                        || labeledSentinelRow
                        || spec.skipUnidentifiedRows()) {
                    continue;
                }
                throw new SsmtParseException(
                        "Blank CSV identity in columns " + spec.identityColumns()
                                + " at record " + record.getRecordNumber()
                                + "; localizable values="
                                + presentTextColumns.stream().map(record::get).toList(),
                        request.sourceFile());
            }
            if (!identities.add(identity)) {
                throw new SsmtParseException(
                        "Duplicate CSV identity: " + identity,
                        request.sourceFile());
            }
            for (String column : presentTextColumns) {
                String text = record.get(column);
                if (!text.isEmpty()) {
                    extracted.add(new ExtractedString(
                            request.modId(),
                            request.relativeSourceFile(),
                            stableKey(identity, column),
                            text,
                            -1));
                }
            }
        }
        extracted.sort(Comparator.comparing(ExtractedString::key));
        return List.copyOf(extracted);
    }

    private List<String> presentTextColumns(CSVParser parser) {
        Set<String> headers = parser.getHeaderMap().keySet();
        return spec.allTextColumns().stream().filter(headers::contains).toList();
    }

    private String stableKey(String identity, String column) {
        return "csv:%s=%s:%s".formatted(
                encode(String.join(",", spec.identityColumns())),
                encode(identity),
                encode(column));
    }

    private static String encode(String value) {
        return value.replace("%", "%25")
                .replace("\0", "%00")
                .replace(",", "%2C")
                .replace(":", "%3A")
                .replace("=", "%3D");
    }
}
