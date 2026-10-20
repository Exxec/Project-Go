package com.ssmt.core.evidence;

import java.util.Map;

/** Legacy structural observations; these do not establish live runtime evidence. */
public final class RuntimeEvidence {
    private RuntimeEvidence() { }

    public record Candidate(String modId, String candidateId, String buildHash) { }
    public record Ledger(Candidate candidate, long totalBytes, int entryCount,
                         int structuralGaps, String verdict, String finding) { }

    public static Ledger of(Candidate candidate, long totalBytes, int entryCount, int gaps) {
        Ledger ledger = new Ledger(candidate, totalBytes, entryCount, gaps,
                gaps > 0 ? "FAIL" : "NOT_TESTED",
                gaps > 0 ? "Structural gaps detected" : "Live runtime evidence not observed");
        SchemaValidator.validateLedger(ledger);
        return ledger;
    }

    public static void validateJson(Map<String, Object> json) {
        SchemaValidator.validateJson(json);
    }
}
