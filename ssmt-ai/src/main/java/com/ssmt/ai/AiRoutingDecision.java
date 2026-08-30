package com.ssmt.ai;

/** Routing recommendation only; never a translation-quality or acceptance score. */
public enum AiRoutingDecision {
    USE_LOCAL,
    OPTIONAL_AI_REVIEW,
    INVOKE_AI_IF_ENABLED
}
