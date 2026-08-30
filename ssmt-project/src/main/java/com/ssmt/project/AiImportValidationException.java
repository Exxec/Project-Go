package com.ssmt.project;

import java.util.List;

/**
 * AI response rejection with structured deterministic diagnostics.
 */
public final class AiImportValidationException extends ProjectException {
    private static final long serialVersionUID = 1L;
    private final java.util.ArrayList<AiImportDiagnostic> diagnostics;

    /**
     * Creates a validation failure.
     *
     * @param diagnostic validation detail
     */
    public AiImportValidationException(AiImportDiagnostic diagnostic) {
        super(diagnostic.message());
        diagnostics = new java.util.ArrayList<>(List.of(diagnostic));
    }

    /**
     * @return immutable diagnostics
     */
    public List<AiImportDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }
}
