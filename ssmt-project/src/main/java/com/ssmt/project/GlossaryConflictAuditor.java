package com.ssmt.project;

import java.util.ArrayList;
import java.util.List;

/** Deterministically flags explicit glossary conflicts without changing text. */
public final class GlossaryConflictAuditor {
    public List<GlossaryConflict> inspect(LocalizationProject project, GlossaryDocument glossary) {
        List<GlossaryConflict> findings = new ArrayList<>();
        for (ProjectEntry entry : project.entries()) {
            if (entry.translatedText().isBlank()) {
                continue;
            }
            for (GlossaryTerm term : glossary.terms()) {
                if (entry.originalText().contains(term.source())
                        && !entry.translatedText().contains(term.target())) {
                    findings.add(new GlossaryConflict(entry, term));
                }
            }
        }
        return List.copyOf(findings);
    }
}
