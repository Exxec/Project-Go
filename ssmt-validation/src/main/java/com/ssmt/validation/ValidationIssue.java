package com.ssmt.validation;

/**
 * A structured validation finding.
 *
 * @param code stable finding category
 * @param message human-readable explanation
 */
public record ValidationIssue(ValidationCode code, String message) {
    /**
     * Validates issue content.
     */
    public ValidationIssue {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
