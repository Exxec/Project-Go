package com.ssmt.core.evidence;

import java.util.Map;
import java.util.Set;

/** Validates the legacy structural snapshot without accepting runtime PASS claims. */
public final class SchemaValidator {
    private SchemaValidator() { }

    public static void validateLedger(RuntimeEvidence.Ledger ledger) {
        if (ledger == null || ledger.candidate() == null) {
            throw new IllegalArgumentException("Ledger candidate required");
        }
        var candidate = ledger.candidate();
        requireText(candidate.modId());
        requireText(candidate.candidateId());
        if (candidate.buildHash() == null || !candidate.buildHash().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Build hash must be lowercase SHA-256");
        }
        if (ledger.totalBytes() < 0 || ledger.entryCount() < 0 || ledger.structuralGaps() < 0) {
            throw new IllegalArgumentException("Negative evidence counts");
        }
        if (!Set.of("FAIL", "NOT_TESTED", "NOT_APPLICABLE").contains(ledger.verdict())) {
            throw new IllegalArgumentException("Structural snapshots cannot assert runtime PASS");
        }
        requireText(ledger.finding());
    }

    public static void validateJson(Map<String, Object> json) {
        if (json == null || !(json.get("candidate") instanceof Map<?, ?> candidate)) {
            throw new IllegalArgumentException("Candidate object required");
        }
        validateLedger(new RuntimeEvidence.Ledger(
                new RuntimeEvidence.Candidate(text(candidate.get("mod_id")),
                        text(candidate.get("candidate_id")), text(candidate.get("build_hash"))),
                count(json.get("total_bytes")), integer(json.get("entry_count")),
                integer(json.get("structural_gaps")), text(json.get("verdict")),
                text(json.get("finding"))));
    }

    private static long count(Object value) {
        if (!(value instanceof Long) && !(value instanceof Integer)) {
            throw new IllegalArgumentException("Integral count required");
        }
        return ((Number) value).longValue();
    }

    private static int integer(Object value) {
        return Math.toIntExact(count(value));
    }

    private static String text(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Text field required");
        }
        requireText(text);
        return text;
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Nonblank field required");
        }
    }
}
