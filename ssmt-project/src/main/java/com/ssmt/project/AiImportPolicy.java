package com.ssmt.project;

/** Controls whether validated external-AI results remain drafts or are explicitly approved. */
public enum AiImportPolicy {
    REVIEW_DRAFTS,
    APPROVE_ALL_VALIDATED,
    /** Per unit: a draft that passes every rule in {@link PolicyApproval} is marked POLICY_APPROVED, others stay drafts. */
    APPROVE_BY_RULES
}
