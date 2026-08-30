package com.ssmt.ai;

import java.util.Objects;

/** Device diagnostics for the most recent Argos translation. */
public record ArgosExecutionStatus(
        ArgosDevice requestedDevice,
        TranslationBackend usedBackend,
        String fallbackReason) {

    public ArgosExecutionStatus {
        requestedDevice = Objects.requireNonNull(requestedDevice, "requestedDevice");
        usedBackend = Objects.requireNonNull(usedBackend, "usedBackend");
        fallbackReason = Objects.requireNonNullElse(fallbackReason, "");
    }
}
