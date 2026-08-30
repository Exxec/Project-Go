package com.ssmt.extractor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One file no extractor recognized that appears to contain non-ASCII text,
 * flagged for human review rather than translated automatically.
 *
 * @param relativeSourceFile file path relative to the mod root
 * @param sample short excerpt around the first non-ASCII run found
 */
public record CoverageGapFinding(Path relativeSourceFile, String sample) {

    public CoverageGapFinding {
        Objects.requireNonNull(relativeSourceFile, "relativeSourceFile must not be null");
        Objects.requireNonNull(sample, "sample must not be null");
    }
}
