package com.ssmt.core.model;

/**
 * Trusted, closed vocabulary describing where a translation originated.
 */
public enum TranslationProvenance {
    HUMAN_EDITED(5),
    AUTHOR_LOCALIZATION(4),
    MANUAL_IMPORT(3),
    AI_TRANSLATED(2),
    ARGOS_TRANSLATED(2),
    TRANSLATE_LOCALLY(2),
    FUZZY_MATCH(1);

    private final int preference;

    TranslationProvenance(int preference) {
        this.preference = preference;
    }

    /**
     * @return larger values for preferred translation sources
     */
    public int preference() {
        return preference;
    }

    /**
     * Returns whether this provenance may replace another automatically.
     *
     * @param existing currently stored provenance
     * @return true only when this source is strictly preferred
     */
    public boolean preferredOver(TranslationProvenance existing) {
        return preference > existing.preference;
    }
}
