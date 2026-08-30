package com.ssmt.project;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned data-only glossary document; loading it executes no code. */
public record GlossaryDocument(int schemaVersion, String sourceLanguage,
        String targetLanguage, List<GlossaryTerm> terms) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public GlossaryDocument {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported glossary schema version " + schemaVersion);
        }
        if (sourceLanguage == null || sourceLanguage.isBlank()
                || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("Glossary languages must not be blank");
        }
        terms = List.copyOf(terms);
        Set<String> sources = new HashSet<>();
        for (GlossaryTerm term : terms) {
            if (!sources.add(term.source())) {
                throw new IllegalArgumentException("Duplicate glossary source term " + term.source());
            }
        }
    }
}
