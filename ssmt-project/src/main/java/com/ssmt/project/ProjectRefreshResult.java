package com.ssmt.project;

/**
 * Transactional refresh candidate and its dry-run report.
 *
 * @param project candidate project; not persisted automatically
 * @param report deterministic reconciliation report
 */
public record ProjectRefreshResult(
        LocalizationProject project,
        ReconciliationReport report) {
}
