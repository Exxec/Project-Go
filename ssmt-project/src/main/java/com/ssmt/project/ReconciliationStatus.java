package com.ssmt.project;

/**
 * Classification of one entry during project refresh.
 */
public enum ReconciliationStatus {
    UNCHANGED,
    CHANGED,
    ADDED,
    REMOVED,
    CONFLICTED
}
