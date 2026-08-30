package com.ssmt.gui;

import com.ssmt.validation.ValidationIssue;
import java.util.List;

/**
 * Immutable translation editor row.
 *
 * @param id stable row id
 * @param sourceText exact source text
 * @param translatedText current draft, possibly empty
 * @param issues current validation findings
 */
public record TranslationRow(
        TranslationRowId id,
        String sourceText,
        String translatedText,
        List<ValidationIssue> issues) {
    /**
     * Defensively copies row state.
     */
    public TranslationRow {
        if (id == null || sourceText == null || translatedText == null || issues == null) {
            throw new IllegalArgumentException("Translation row values must not be null");
        }
        issues = List.copyOf(issues);
    }
}
