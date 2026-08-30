package com.ssmt.ai;

import java.util.Locale;

/** User-selected Argos Translate execution device. */
public enum ArgosDevice {
    CPU,
    AUTO,
    CUDA;

    String environmentValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
