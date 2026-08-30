package com.ssmt.validation;

/**
 * Stable categories of translation validation failure.
 */
public enum ValidationCode {
    PRINTF_PLACEHOLDER_MISMATCH,
    MESSAGE_ARGUMENT_MISMATCH,
    DOLLAR_TOKEN_MISMATCH,
    LINE_BREAK_MISMATCH,
    MALFORMED_PRINTF,
    MALFORMED_MESSAGE_ARGUMENT
}
