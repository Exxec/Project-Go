package com.ssmt.project;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Reports that one mod id already has saved translation work whose source text
 * shares nothing with the mod the user just selected.
 *
 * <p>Project Go never merges two genuinely different mods or forks silently. The
 * user chooses between keeping the previous translation and translating the new
 * copy separately. The message deliberately names the mods only: digests, source
 * paths, fingerprints, and internal workspace names stay backstage.</p>
 */
public final class SourceIdentityConflictException extends ProjectException {
    private static final long serialVersionUID = 1L;
    private final String sourcePath;
    private final String previousModName;
    private final String currentModName;
    private final int previousEntries;
    private final int currentEntries;

    /**
     * Describes one unresolved same-id/different-source conflict.
     *
     * @param source mod root the user selected
     * @param previousModName display name recorded with the saved work
     * @param currentModName display name declared by the selected mod
     * @param previousEntries number of saved source texts
     * @param currentEntries number of source texts in the selected mod
     */
    public SourceIdentityConflictException(
            Path source,
            String previousModName,
            String currentModName,
            int previousEntries,
            int currentEntries) {
        super("This appears to be a different version or fork of a mod you have translated before."
                + " Your saved work belongs to \u201c" + previousModName + "\u201d ("
                + previousEntries + " texts), while this copy is \u201c" + currentModName
                + "\u201d (" + currentEntries + " texts) and shares none of them."
                + " Choose whether to use the previous translation or to start separately.");
        this.sourcePath = Objects.requireNonNull(source, "source").toString();
        this.previousModName = Objects.requireNonNull(previousModName, "previousModName");
        this.currentModName = Objects.requireNonNull(currentModName, "currentModName");
        this.previousEntries = previousEntries;
        this.currentEntries = currentEntries;
    }

    /** Returns the mod root the user selected. */
    public Path source() {
        return Path.of(sourcePath);
    }

    /** Returns the display name recorded with the saved work. */
    public String previousModName() {
        return previousModName;
    }

    /** Returns the display name declared by the selected mod. */
    public String currentModName() {
        return currentModName;
    }

    /** Returns how many source texts the saved work holds. */
    public int previousEntries() {
        return previousEntries;
    }

    /** Returns how many source texts the selected mod holds. */
    public int currentEntries() {
        return currentEntries;
    }
}
