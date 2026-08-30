package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LogDashboardViewModelTest {
    @Test
    void keepsOnlyNewestEntriesWithinBound() {
        LogDashboardViewModel model = new LogDashboardViewModel(2);
        model.append(new LogEntry(Instant.EPOCH, LogLevel.INFO, "one"));
        model.append(new LogEntry(Instant.EPOCH.plusSeconds(1), LogLevel.WARN, "two"));
        model.append(new LogEntry(Instant.EPOCH.plusSeconds(2), LogLevel.ERROR, "three"));

        assertThat(model.entries()).extracting(LogEntry::message)
                .containsExactly("two", "three");
    }
}
