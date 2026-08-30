package com.ssmt.project;

import com.ssmt.validation.font.BmFontGlyphSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only check for translated text a given Starsector bitmap font cannot render.
 * Findings are a warning, never a build failure — the concrete fix is either
 * translating the text differently or using/adding a font with wider coverage.
 */
public final class FontCoverageAuditor {
    /**
     * Audits translated entries against one font without changing the project.
     *
     * @param project project to inspect
     * @param font font whose glyph coverage to check against
     * @return structured findings in deterministic order
     */
    public List<FontCoverageFinding> audit(LocalizationProject project, BmFontGlyphSet font) {
        List<FontCoverageFinding> findings = new ArrayList<>();
        for (ProjectEntry entry : project.entries()) {
            if (entry.translatedText().isBlank()) {
                continue;
            }
            List<Integer> missing = font.findMissing(entry.translatedText());
            if (!missing.isEmpty()) {
                findings.add(new FontCoverageFinding(
                        entry.sourceFile(), entry.key(), entry.translatedText(), missing));
            }
        }
        findings.sort(Comparator
                .comparing((FontCoverageFinding finding) -> finding.sourceFile().toString())
                .thenComparing(FontCoverageFinding::key));
        return List.copyOf(findings);
    }
}
