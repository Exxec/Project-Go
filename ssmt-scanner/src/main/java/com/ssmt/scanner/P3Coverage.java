package com.ssmt.scanner;

import java.util.List;

/** Compatibility facade for read-only candidate metadata inventory. */
public final class P3Coverage {
    private P3Coverage() { }

    public static List<CoverageRunner.Report> report(String path) {
        return CoverageRunner.run(path, false);
    }
}
