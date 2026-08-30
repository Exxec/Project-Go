package com.ssmt.ai;

/** Simple user-facing provider-routing modes. */
public enum TranslationMode {
    LOCAL_ONLY(false),
    SMART_DEFAULT(true),
    AI_ASSISTED(true);

    private final boolean allowsAi;

    TranslationMode(boolean allowsAi) {
        this.allowsAi = allowsAi;
    }

    public boolean allowsAi() {
        return allowsAi;
    }
}
