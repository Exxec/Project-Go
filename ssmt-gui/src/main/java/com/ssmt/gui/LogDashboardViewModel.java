package com.ssmt.gui;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Bounded ordered dashboard log state.
 */
public final class LogDashboardViewModel {
    private final int capacity;
    private final ArrayDeque<LogEntry> entries = new ArrayDeque<>();

    public LogDashboardViewModel(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * Appends an event and evicts the oldest when full.
     *
     * @param entry structured event
     */
    public void append(LogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    /**
     * @return immutable oldest-to-newest events
     */
    public List<LogEntry> entries() {
        return List.copyOf(entries);
    }
}
