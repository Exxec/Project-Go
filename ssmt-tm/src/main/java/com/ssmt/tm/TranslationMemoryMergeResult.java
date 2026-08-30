package com.ssmt.tm;

/**
 * Deterministic comparison/merge summary for two translation catalogs.
 *
 * @param sourceEntries entries inspected in the source catalog
 * @param added identities missing from the destination
 * @param upgraded identities replaced by strictly higher-preference provenance
 * @param identical identities already containing the same translation
 * @param conflicts differing translations deliberately left unchanged
 */
public record TranslationMemoryMergeResult(
        int sourceEntries,
        int added,
        int upgraded,
        int identical,
        int conflicts) {

    /**
     * @return entries that a merge will safely write
     */
    public int changes() {
        return added + upgraded;
    }
}
