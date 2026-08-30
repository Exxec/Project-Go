package com.ssmt.scanner;

import com.ssmt.core.model.ModInfo;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of scanning a directory of Starsector mods.
 *
 * @param mods successfully parsed mods in dependency-first order
 * @param warnings non-fatal problems encountered during discovery
 */
public record ScanReport(List<ModInfo> mods, List<String> warnings) {

    public ScanReport {
        Objects.requireNonNull(mods, "mods must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        mods = List.copyOf(mods);
        warnings = List.copyOf(warnings);
    }
}
