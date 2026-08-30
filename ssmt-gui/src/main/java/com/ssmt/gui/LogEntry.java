package com.ssmt.gui;

import java.time.Instant;

/**
 * Structured dashboard event.
 *
 * @param timestamp event time
 * @param level severity
 * @param message display text
 */
public record LogEntry(Instant timestamp, LogLevel level, String message) {
    /**
     * Validates event content.
     */
    public LogEntry {
        if (timestamp == null || level == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Log entry values must not be null or blank");
        }
    }
}
