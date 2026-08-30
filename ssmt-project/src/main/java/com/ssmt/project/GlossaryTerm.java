package com.ssmt.project;

/** One explicit, shareable source-to-target terminology rule. */
public record GlossaryTerm(String source, String target, String note) {
    public GlossaryTerm {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            throw new IllegalArgumentException("Glossary source and target must not be blank");
        }
        source = source.trim();
        target = target.trim();
        note = note == null ? "" : note.trim();
    }
}
