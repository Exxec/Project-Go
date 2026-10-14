package com.ssmt.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Environmental test failures that are tolerated until an expiry date. The file is tab separated:
 * {@code Class#method<TAB>yyyy-MM-dd<TAB>reason}; lines starting with {@code #} are comments.
 */
public final class KnownFailures {
    /** System property naming the file; set by the build for every test task. */
    public static final String PROPERTY = "ssmt.knownFailures";

    /** One tolerated failure. */
    public record Entry(String test, LocalDate expires, String reason) {
        public boolean activeOn(LocalDate today) {
            return !today.isAfter(expires);
        }
    }

    private final List<Entry> entries;

    private KnownFailures(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** Parses the file text; malformed lines are rejected so a typo cannot silently tolerate nothing or everything. */
    public static KnownFailures parse(String text) {
        List<Entry> result = new ArrayList<>();
        int number = 0;
        for (String line : text.split("\\R", -1)) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\t", 3);
            if (columns.length < 3 || columns[0].isBlank() || columns[2].isBlank()) {
                throw new IllegalArgumentException("Known-failures line " + number
                        + " needs test<TAB>expires<TAB>reason");
            }
            try {
                result.add(new Entry(columns[0].strip(), LocalDate.parse(columns[1].strip()), columns[2].strip()));
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Known-failures line " + number + " has an invalid date", exception);
            }
        }
        return new KnownFailures(result);
    }

    /** Reads the file named by the system property; an absent property or file means nothing is tolerated. */
    public static KnownFailures load() {
        String configured = System.getProperty(PROPERTY);
        if (configured == null) {
            return new KnownFailures(List.of());
        }
        Path path = Path.of(configured);
        if (!Files.isRegularFile(path)) {
            return new KnownFailures(List.of());
        }
        try {
            return parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read known-failures file " + path, exception);
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> find(String test) {
        return entries.stream().filter(entry -> entry.test().equals(test)).findFirst();
    }
}
