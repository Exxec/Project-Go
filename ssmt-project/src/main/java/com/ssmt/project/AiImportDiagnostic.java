package com.ssmt.project;

/**
 * Structured fail-closed AI import diagnostic.
 *
 * @param code stable machine-readable code
 * @param entryId affected entry identity, or empty for document errors
 * @param message human-readable detail
 */
public record AiImportDiagnostic(
        String code,
        String entryId,
        String message) implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
}
