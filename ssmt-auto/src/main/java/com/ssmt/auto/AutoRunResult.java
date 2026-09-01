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
        PATCH_PUBLISHED,
        PATCH_UNCHANGED
    }
}
