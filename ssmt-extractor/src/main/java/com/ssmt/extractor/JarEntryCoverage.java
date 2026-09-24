package com.ssmt.extractor;

import com.ssmt.core.exception.SsmtParseException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
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
        if (!jar.getFileSystem().equals(FileSystems.getDefault())) {
            try (var zip = new ZipInputStream(Files.newInputStream(jar))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (entry.isDirectory()) { continue; }
                    String name = entry.getName();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".class")) {
                        coverage.add(unsupported(name));
                        continue;
                    }
                    coverage.add(classCoverage(name, new ClassReader(zip).getClassName(), selected));
                }
            } catch (IOException | RuntimeException exception) {
                throw new SsmtParseException("Could not inspect JAR entry coverage", jar, exception);
            }
            coverage.sort(Comparator.comparing(Entry::path));
            return List.copyOf(coverage);
        }
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : zip.stream().filter(item -> !item.isDirectory())
                    .sorted(Comparator.comparing(java.util.zip.ZipEntry::getName)).toList()) {
                if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".class")) {
                    coverage.add(unsupported(entry.getName()));
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    coverage.add(classCoverage(entry.getName(),
                            new ClassReader(input).getClassName(), selected));
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new SsmtParseException("Could not inspect JAR entry coverage", jar, exception);
        }
        return List.copyOf(coverage);
    }

    private static Entry unsupported(String name) {
        return new Entry(name, "UNSUPPORTED", 0,
                name.toLowerCase(Locale.ROOT).endsWith(".java")
                        ? "BUNDLED_SOURCE_NOT_A_LOCALIZATION_INPUT"
                        : "NO_STANDARD_ARCHIVE_ENTRY_EXTRACTOR");
    }

    private static Entry classCoverage(String name, String className, List<String> selected) {
        String prefix = "class:" + className + "#";
        int count = (int) selected.stream().filter(key -> key.startsWith(prefix)).count();
        return new Entry(name, count == 0 ? "SUPPORTED_NO_STRINGS" : "EXTRACTED", count,
                count == 0 ? "NO_ALLOWLISTED_STRINGS_SELECTED"
                        : "ALLOWLISTED_CLASS_STRINGS_SELECTED");
    }
}
