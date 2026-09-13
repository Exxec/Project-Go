package com.ssmt.patcher;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

/** Replaces changed CSV fields without rewriting the other cells or record suffix. */
final class CsvTokenPatch {
    private CsvTokenPatch() {
    }

    private record Span(int start, int end) {
    }

    static String patchRow(String raw, List<String> headers, Map<String, String> original,
            Map<String, String> current) throws IOException, PatchBuilderException {
        if (original.equals(current)) {
            return raw;
        }
        List<Span> spans = spans(raw);
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(raw))) {
            var records = parser.getRecords();
            if (records.isEmpty() || records.getFirst().size() != spans.size()) {
                throw new PatchBuilderException("CSV token boundaries could not be verified");
            }
            var record = records.getFirst();
            StringBuilder output = new StringBuilder();
            int copied = 0;
            for (int column = 0; column < headers.size(); column++) {
                String header = headers.get(column);
                if (original.get(header).equals(current.get(header))) {
                    continue;
                }
                if (column >= spans.size() || !record.get(column).equals(original.get(header))) {
                    throw new PatchBuilderException("Missing or ambiguous CSV field: " + header);
                }
                Span span = spans.get(column);
                output.append(raw, copied, span.start());
                output.append(CSVFormat.DEFAULT.format(current.get(header)));
                copied = span.end();
            }
            return output.append(raw, copied, raw.length()).toString();
        }
    }

    private static List<Span> spans(String raw) throws PatchBuilderException {
        List<Span> result = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        // Commons CSV includes ignored empty lines in a record's character range.
        // They are prefix text, not an empty first cell of the selected record.
        while (start < raw.length() && (raw.charAt(start) == '\r' || raw.charAt(start) == '\n')) {
            start++;
        }
        for (int index = start; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < raw.length() && raw.charAt(index + 1) == '"') {
                    index++;
                } else if (quoted || index == start) {
                    quoted = !quoted;
                }
            } else if (!quoted && (character == ',' || character == '\r' || character == '\n')) {
                result.add(new Span(start, index));
                if (character != ',') {
                    return result;
                }
                start = index + 1;
            }
        }
        if (quoted) {
            throw new PatchBuilderException("Unterminated quoted CSV field");
        }
        result.add(new Span(start, raw.length()));
        return result;
    }
}
