package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssuranceSummaryTest {
    private static final String HASH = "a".repeat(64);

    private List<AssuranceSummary.Result> results() {
        var results = new ArrayList<AssuranceSummary.Result>();
        for (var gate : AssuranceSummary.Gate.values()) {
            results.add(result(gate, AssuranceSummary.Disposition.PASS));
        }
        return results;
    }

    private AssuranceSummary.Result result(AssuranceSummary.Gate gate,
            AssuranceSummary.Disposition disposition) {
        return new AssuranceSummary.Result(gate, disposition, HASH, gate.name(),
                "reports/scenario.log", "explicit disposition");
    }

    @Test void runtimePendingDoesNotInheritPassedBuild() {
        var results = results();
        results.removeIf(result -> result.gate() == AssuranceSummary.Gate.COMBAT);
        results.add(result(AssuranceSummary.Gate.COMBAT, AssuranceSummary.Disposition.NOT_TESTED));
        assertThat(new AssuranceSummary().summarize(HASH, results).status())
                .isEqualTo("READY_FOR_LIVE_TEST");
    }

    @Test void requiresCompleteUniqueCandidateBoundLedger() {
        var results = results();
        assertThat(new AssuranceSummary().summarize(HASH, results).status()).isEqualTo("READY");
        results.add(results.getFirst());
        assertThatThrownBy(() -> new AssuranceSummary().summarize(HASH, results))
                .hasMessageContaining("Repeated");
        assertThatThrownBy(() -> new AssuranceSummary().summarize(HASH, List.of()))
                .hasMessageContaining("Missing");
        assertThatThrownBy(() -> new AssuranceSummary().summarize("b".repeat(64), results()))
                .hasMessageContaining("different candidate");
    }

    @Test void unresolvedAuthorityCannotBecomeReadyForLiveTest() {
        var results = results();
        results.removeIf(result -> result.gate() == AssuranceSummary.Gate.SOURCE_AUTHORITY);
        results.add(result(AssuranceSummary.Gate.SOURCE_AUTHORITY,
                AssuranceSummary.Disposition.NOT_TESTED));
        assertThat(new AssuranceSummary().summarize(HASH, results).status())
                .isEqualTo("ESCALATION_REQUIRED");
    }

    @Test void rejectsUnsupportedPassAndWaivedRights() {
        assertThatThrownBy(() -> new AssuranceSummary.Result(AssuranceSummary.Gate.BUILD,
                AssuranceSummary.Disposition.PASS, HASH, "compile", "", ""))
                .hasMessageContaining("evidence");
        assertThatThrownBy(() -> result(AssuranceSummary.Gate.REDISTRIBUTION_RIGHTS,
                AssuranceSummary.Disposition.NOT_APPLICABLE)).hasMessageContaining("cannot be waived");
    }
}
