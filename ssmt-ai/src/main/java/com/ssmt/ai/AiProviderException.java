package com.ssmt.ai;

/**
 * Reports optional AI provider transport or response failures.
 */
public final class AiProviderException extends Exception {
    private static final long serialVersionUID = 1L;

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
