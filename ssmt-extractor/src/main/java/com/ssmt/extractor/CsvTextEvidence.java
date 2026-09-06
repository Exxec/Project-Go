package com.ssmt.extractor;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;

/** Header-based ASCII evidence for advisory CSV coverage suggestions. */
final class CsvTextEvidence {
    private static final Set<String> TEXT_HEADERS = Set.of("name", "displayname", "description",
            "additionaldescription", "shortdescription", "longdescription", "flavortext",
            "message", "text", "text1", "text2", "text3", "text4", "help", "desc");

    private CsvTextEvidence() {
    }

    static boolean matches(String header, String value) {
        return TEXT_HEADERS.contains(header.strip().toLowerCase(Locale.ROOT))
                && !value.isBlank() && value.chars().anyMatch(Character::isLetter);
    }

    static Optional<String> sample(String text) {
        String withoutBom = text.startsWith("\uFEFF") ? text.substring(1) : text;
        try (var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(true).get().parse(new StringReader(withoutBom))) {
            var headers = parser.getHeaderNames();
            for (var record : parser) {
                if (record.size() > 0 && record.get(0).stripLeading().startsWith("#")) {
                    continue;
                }
                for (int index = 0; index < Math.min(headers.size(), record.size()); index++) {
                    if (matches(headers.get(index), record.get(index))) {
                        return Optional.of(record.get(index));
                    }
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            // An unreadable ASCII table provides no reliable header-based evidence.
            return Optional.empty();
        }
        return Optional.empty();
    }
}
