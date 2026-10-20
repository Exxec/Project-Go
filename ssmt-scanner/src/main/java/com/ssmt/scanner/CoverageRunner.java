package com.ssmt.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Metadata inventory only. Semantic coverage requires the extractor's coverage report. */
public final class CoverageRunner {
    private CoverageRunner() { }

    public record Entry(String path, long bytes) { }
    public record Report(String candidate, int idents, int csvs, int jsons, int nested,
                         int total, int gaps, int status, String verdict) { }

    /** Never upgrades filename observations into semantic coverage or translation-unit counts. */
    public static List<Report> run(String candidatePath, boolean blockMode) {
        try {
            var entries = new ArchiveInventory().capture(Path.of(candidatePath));
            int metadata = 0;
            int csvs = 0;
            int jsons = 0;
            int jars = 0;
            for (var entry : entries) {
                String name = entry.path().toLowerCase(Locale.ROOT);
                if (name.equals("mod_info.json") || name.endsWith("/mod_info.json")) {
                    metadata++;
                }
                if (name.endsWith(".csv")) { csvs++; }
                if (name.endsWith(".json")) { jsons++; }
                if (name.endsWith(".jar")) { jars++; }
            }
            return List.of(new Report(candidatePath, metadata, csvs, jsons, jars,
                    0, 0, blockMode ? 2 : 3, blockMode ? "FAIL" : "NOT_TESTED"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inventory candidate", exception);
        }
    }
}
