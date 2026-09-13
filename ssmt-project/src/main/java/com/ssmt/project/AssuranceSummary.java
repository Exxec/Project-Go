package com.ssmt.project;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Strict evidence ledger; recorded results are not independently verified by this model. */
public final class AssuranceSummary {
    /** Independent assurance dimensions; none inherits another gate's result. */
    public enum Gate {
        SOURCE_REVIEW, SOURCE_AUTHORITY, BUILD, STATIC_VALIDATION, DEPENDENCY_CHECK,
        API_SIGNATURE_CHECK, PACKAGE_IDENTITY, REDISTRIBUTION_RIGHTS,
        AUTOMATED_BOOT, CAMPAIGN, COMBAT, SAVE_RELOAD, UPGRADE_COMPATIBILITY
    }

    /** Explicit disposition, including unperformed or uncertain work. */
    public enum Disposition { PASS, FAIL, NOT_TESTED, NOT_APPLICABLE, REVIEW_REQUIRED }

    /** One scenario result bound to the exact candidate bytes. */
    public record Result(Gate gate, Disposition disposition, String candidateSha256,
            String scenario, String evidence, String reason) {
        public Result {
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(disposition, "disposition");
            requireHash(candidateSha256);
            if (scenario == null || scenario.isBlank()) {
                throw new IllegalArgumentException("Every result requires a named scenario");
            }
            evidence = Objects.requireNonNullElse(evidence, "");
            reason = Objects.requireNonNullElse(reason, "");
            if ((disposition == Disposition.PASS || disposition == Disposition.FAIL)
                    && evidence.isBlank()) {
                throw new IllegalArgumentException("PASS/FAIL requires an evidence reference");
            }
            if ((disposition == Disposition.NOT_APPLICABLE
                    || disposition == Disposition.REVIEW_REQUIRED) && reason.isBlank()) {
                throw new IllegalArgumentException("Disposition requires a written reason");
            }
            if (gate == Gate.REDISTRIBUTION_RIGHTS && disposition == Disposition.NOT_APPLICABLE) {
                throw new IllegalArgumentException("Redistribution rights cannot be waived as not applicable");
            }
        }
    }

    /** Derived state, never an input completion declaration. */
    public record Summary(String candidateSha256, String status, List<Result> results) {
        public Summary { results = List.copyOf(results); }
    }

    /** Rejects missing, repeated and foreign-candidate gates before deriving status. */
    public Summary summarize(String candidateSha256, List<Result> results) {
        requireHash(candidateSha256);
        var byGate = new EnumMap<Gate, Result>(Gate.class);
        for (Result result : List.copyOf(results)) {
            if (!result.candidateSha256().equals(candidateSha256)) {
                throw new IllegalArgumentException("Evidence belongs to a different candidate");
            }
            if (byGate.putIfAbsent(result.gate(), result) != null) {
                throw new IllegalArgumentException("Repeated or contradictory gate: " + result.gate());
            }
        }
        for (Gate gate : Gate.values()) {
            if (!byGate.containsKey(gate)) {
                throw new IllegalArgumentException("Missing explicit gate: " + gate);
            }
        }
        String status = "READY";
        if (byGate.values().stream().anyMatch(result -> result.disposition() == Disposition.FAIL)) {
            status = "FAILED";
        } else if (byGate.values().stream().anyMatch(result ->
                result.disposition() == Disposition.REVIEW_REQUIRED
                || (result.disposition() == Disposition.NOT_TESTED && !runtime(result.gate())))) {
            status = "ESCALATION_REQUIRED";
        } else if (byGate.values().stream().anyMatch(result ->
                result.disposition() == Disposition.NOT_TESTED)) {
            status = "READY_FOR_LIVE_TEST";
        }
        return new Summary(candidateSha256, status, List.copyOf(byGate.values()));
    }

    private static boolean runtime(Gate gate) {
        return switch (gate) {
            case AUTOMATED_BOOT, CAMPAIGN, COMBAT, SAVE_RELOAD, UPGRADE_COMPATIBILITY -> true;
            default -> false;
        };
    }

    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Candidate SHA-256 must be 64 lowercase hex characters");
        }
    }
}
