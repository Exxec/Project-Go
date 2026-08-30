package com.ssmt.tm;

/**
 * A translation-memory candidate and its normalized similarity score.
 *
 * @param entry matching entry
 * @param score similarity from zero to one
 */
public record TranslationMatch(TranslationEntry entry, double score) {
    /**
     * Validates the result.
     */
    public TranslationMatch {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0 and 1");
        }
    }
}
