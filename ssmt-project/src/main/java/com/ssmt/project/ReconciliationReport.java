package com.ssmt.project;

import java.util.List;

/**
 * Deterministically ordered, read-only refresh report.
 *
 * @param entries reconciliation findings
 */
public record ReconciliationReport(List<ReconciliationEntry> entries) {
    public ReconciliationReport {
        entries = List.copyOf(entries);
    }

    @Override
    public List<ReconciliationEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Counts findings of one status.
     *
     * @param status requested classification
     * @return matching finding count
     */
    public long count(ReconciliationStatus status) {
        return entries.stream().filter(entry -> entry.status() == status).count();
    }
}
