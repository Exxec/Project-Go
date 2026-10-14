package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.testsupport.KnownFailures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KnownFailuresTest {
    private static final String FILE = "# comment\n\na.B#c\t2026-12-31\tflaky disk\n";

    @Test void parsesEntriesAndHonoursTheExpiryDate() {
        var entry = KnownFailures.parse(FILE).find("a.B#c").orElseThrow();
        assertThat(entry.reason()).isEqualTo("flaky disk");
        assertThat(entry.activeOn(LocalDate.parse("2026-12-31"))).isTrue();
        assertThat(entry.activeOn(LocalDate.parse("2027-01-01"))).isFalse();
        assertThat(KnownFailures.parse(FILE).find("a.B#other")).isEmpty();
    }

    @Test void rejectsMalformedLinesInsteadOfSilentlyTolerating() {
        assertThatThrownBy(() -> KnownFailures.parse("a.B#c\t2026-12-31")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnownFailures.parse("a.B#c\tsoon\twhy")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test void theCommittedFileParsesAndHasNoExpiredEntries() {
        var committed = KnownFailures.load();
        assertThat(committed.entries()).allMatch(entry -> entry.activeOn(LocalDate.now()),
                "an expired known-failures entry must be fixed or removed");
    }
}
