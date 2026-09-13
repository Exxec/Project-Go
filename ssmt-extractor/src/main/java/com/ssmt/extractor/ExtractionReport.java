package com.ssmt.extractor;

import com.ssmt.core.model.ExtractedString;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic result of extracting all supported files in one mod.
 *
 * @param strings extracted localizable strings
 * @param skippedFiles unsupported regular files, relative to the mod root
 * @param fileCoverage observed handling and counts for every discovered regular file
 */
public record ExtractionReport(
        List<ExtractedString> strings,
        List<Path> skippedFiles,
        List<FileCoverage> fileCoverage
) {

    public ExtractionReport {
        Objects.requireNonNull(strings, "strings must not be null");
        Objects.requireNonNull(skippedFiles, "skippedFiles must not be null");
        strings = List.copyOf(strings);
        skippedFiles = List.copyOf(skippedFiles);
        fileCoverage = List.copyOf(fileCoverage);
    }

    /** Legacy manually supplied reports have no observed coverage inventory. */
    public ExtractionReport(List<ExtractedString> strings, List<Path> skippedFiles) {
        this(strings, skippedFiles, List.of());
    }
}
