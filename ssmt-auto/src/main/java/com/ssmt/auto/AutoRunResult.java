package com.ssmt.auto;

/**
 * Terminal state of one headless automation pass.
 *
 * @param status workflow status
 * @param workspace automation workspace
 * @param detail user-facing next action
 */
public record AutoRunResult(Status status, java.nio.file.Path workspace, String detail) {
    /**
     * Automation outcomes.
     */
    public enum Status {
        MASTER_LIBRARY_NEEDED,
        MASTER_LIBRARY_INCOMPLETE,
        UNREADABLE_INPUTS_NEED_ACCEPTANCE,
        AI_SEND_NEEDS_CONFIRMATION,
        AI_ROUND_TRIP_INTERRUPTED,
        PATCH_PUBLISHED,
        PATCH_UNCHANGED
    }
}
