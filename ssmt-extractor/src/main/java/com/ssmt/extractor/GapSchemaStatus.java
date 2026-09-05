package com.ssmt.extractor;

/**
 * Outcome of inferring one opt-in CSV schema from a coverage-gap finding.
 * Only {@link #SUGGESTED} contributes an entry to a draft catalog; every other
 * status leaves the file advisory, matching {@code ADR-032}'s evidence-gated
 * coverage policy.
 */
public enum GapSchemaStatus {

    /** A complete {@code OptInCsvFileSchema} was inferred and is reviewable. */
    SUGGESTED,

    /** No header named {@code id} and no all-unique non-blank column exists. */
    NO_ID_COLUMN,

    /** An identity column was found, but no column holds non-ASCII data cells. */
    NO_TEXT_COLUMNS,

    /** CSV parsing or schema validation failed; the reason carries the detail. */
    UNPARSEABLE
}
