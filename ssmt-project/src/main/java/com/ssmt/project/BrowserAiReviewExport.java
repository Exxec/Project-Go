package com.ssmt.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Files created for a manual browser-AI review exchange. */
public record BrowserAiReviewExport(Path root, List<Path> parts, Optional<Path> manifest) {
    public BrowserAiReviewExport {
        parts = List.copyOf(parts);
        manifest = manifest == null ? Optional.empty() : manifest;
    }
}
