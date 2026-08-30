package com.ssmt.core.exception;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Indicates that a source file or source directory could not be parsed.
 */
public final class SsmtParseException extends SsmtException {

    private static final long serialVersionUID = 1L;

    private final String sourcePath;
    private final int lineNumber;
    private final String diagnosticCode;
    private final String suggestion;

    public SsmtParseException(String message, Path sourcePath) {
        this(message, sourcePath, -1, null);
    }

    public SsmtParseException(String message, Path sourcePath, Throwable cause) {
        this(message, sourcePath, -1, cause);
    }

    public SsmtParseException(String message, Path sourcePath, int lineNumber, Throwable cause) {
        this(message, sourcePath, lineNumber, cause, "", "");
    }

    public SsmtParseException(
            String message,
            Path sourcePath,
            int lineNumber,
            Throwable cause,
            String diagnosticCode,
            String suggestion) {
        super(formatMessage(message, sourcePath, lineNumber), cause);
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath must not be null").toString();
        if (lineNumber == 0 || lineNumber < -1) {
            throw new IllegalArgumentException("lineNumber must be positive or -1");
        }
        this.lineNumber = lineNumber;
        this.diagnosticCode = Objects.requireNonNull(
                diagnosticCode, "diagnosticCode must not be null");
        this.suggestion = Objects.requireNonNull(suggestion, "suggestion must not be null");
    }

    private static String formatMessage(String message, Path sourcePath, int lineNumber) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        return lineNumber > 0
                ? "%s (%s:%d)".formatted(message, sourcePath, lineNumber)
                : "%s (%s)".formatted(message, sourcePath);
    }

    public Path sourcePath() {
        return Path.of(sourcePath);
    }

    public OptionalInt lineNumber() {
        return lineNumber > 0 ? OptionalInt.of(lineNumber) : OptionalInt.empty();
    }

    /**
     * @return stable diagnostic identifier when a specific parse defect was recognized
     */
    public Optional<String> diagnosticCode() {
        return diagnosticCode.isBlank() ? Optional.empty() : Optional.of(diagnosticCode);
    }

    /**
     * @return non-mutating repair guidance when available
     */
    public Optional<String> suggestion() {
        return suggestion.isBlank() ? Optional.empty() : Optional.of(suggestion);
    }
}
