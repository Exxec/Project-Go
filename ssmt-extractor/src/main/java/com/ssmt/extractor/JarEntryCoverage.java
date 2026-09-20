package com.ssmt.extractor;

import com.ssmt.core.exception.SsmtParseException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;

/** Per-entry handling evidence for a JAR already processed by standard extraction. */
public final class JarEntryCoverage {
    /** One nested payload's exact standard-handler disposition. */
    public record Entry(String path, String status, int selectedStrings, String reason) { }

    /** Maps extracted class identities back to their exact JAR entries. */
    public List<Entry> audit(Path jar, Path relativeJar, ExtractionReport report)
            throws SsmtParseException {
        var selected = report.strings().stream()
                .filter(string -> string.sourceFile().equals(relativeJar))
                .map(com.ssmt.core.model.ExtractedString::key)
                .filter(key -> key.startsWith("class:"))
                .toList();
        var coverage = new ArrayList<Entry>();
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : zip.stream().filter(item -> !item.isDirectory())
                    .sorted(Comparator.comparing(java.util.zip.ZipEntry::getName)).toList()) {
                if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".class")) {
                    coverage.add(new Entry(entry.getName(), "UNSUPPORTED", 0,
                            entry.getName().toLowerCase(Locale.ROOT).endsWith(".java")
                                    ? "BUNDLED_SOURCE_NOT_A_LOCALIZATION_INPUT"
                                    : "NO_STANDARD_ARCHIVE_ENTRY_EXTRACTOR"));
                    continue;
                }
                String className;
                try (InputStream input = zip.getInputStream(entry)) {
                    className = new ClassReader(input).getClassName();
                }
                String prefix = "class:" + className + "#";
                int count = (int) selected.stream().filter(key -> key.startsWith(prefix)).count();
                coverage.add(new Entry(entry.getName(),
                        count == 0 ? "SUPPORTED_NO_STRINGS" : "EXTRACTED", count,
                        count == 0 ? "NO_ALLOWLISTED_STRINGS_SELECTED"
                                : "ALLOWLISTED_CLASS_STRINGS_SELECTED"));
            }
        } catch (IOException | RuntimeException exception) {
            throw new SsmtParseException("Could not inspect JAR entry coverage", jar, exception);
        }
        return List.copyOf(coverage);
    }
}
