package com.ssmt.extractor;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.extractor.json.JsonExtractionSpec;
import com.ssmt.extractor.json.JsonExtractor;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Advisory inventory of JSON string leaves intentionally outside standard schemas. */
public final class StandardJsonGapAuditor {
    private static final long MAX_INPUT_BYTES = 16L * 1024 * 1024;

    /** Review-only finding; a string leaf is not automatically player-visible. */
    public record Finding(Path relativeSourceFile, String status, String pointer, String sample) { }

    /** Compares all textual leaves with the exact pointers selected by standard extraction. */
    public List<Finding> audit(Path modRoot, String modId, ExtractionReport report) {
        Path root = modRoot.toAbsolutePath().normalize();
        Set<String> selected = new HashSet<>();
        for (ExtractedString string : report.strings()) {
            selected.add(key(string.sourceFile(), string.key()));
        }
        var findings = new ArrayList<Finding>();
        for (FileCoverage coverage : report.fileCoverage()) {
            if (!coverage.handler().equals(StandardJsonFileExtractor.class.getName())) { continue; }
            Path relative = coverage.sourceFile();
            Path file = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !file.startsWith(root) || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                findings.add(unavailable(relative, "UNAVAILABLE_UNSAFE_PATH"));
                continue;
            }
            try {
                if (Files.size(file) > MAX_INPUT_BYTES) {
                    findings.add(unavailable(relative, "UNAVAILABLE_INPUT_LIMIT"));
                    continue;
                }
                var request = new ExtractionRequest(modId, root, file);
                for (ExtractedString leaf : new JsonExtractor(JsonExtractionSpec.allTextLeaves())
                        .extract(request)) {
                    if (!selected.contains(key(relative, leaf.key()))) {
                        findings.add(new Finding(relative, "UNSELECTED_TEXT_REVIEW", leaf.key(),
                                sample(leaf.originalText())));
                    }
                }
            } catch (IOException | SsmtParseException exception) {
                findings.add(unavailable(relative, "UNAVAILABLE_READ_OR_PARSE"));
            }
        }
        findings.sort(Comparator.comparing((Finding finding) -> normalized(finding.relativeSourceFile()))
                .thenComparing(Finding::status).thenComparing(Finding::pointer));
        return List.copyOf(findings);
    }

    private static Finding unavailable(Path path, String status) {
        return new Finding(path, status, "", "");
    }

    private static String key(Path path, String pointer) {
        return normalized(path) + "\u0000" + pointer;
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sample(String text) {
        String compact = text.replace('\n', ' ').replace('\r', ' ').strip();
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }
}
