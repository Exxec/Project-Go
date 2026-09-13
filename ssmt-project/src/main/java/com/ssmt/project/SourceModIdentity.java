package com.ssmt.project;

import java.util.Objects;

/**
 * Immutable metadata describing the source mod the user selected.
 *
 * <p>These values are read from the source {@code mod_info.json} and its folder.
 * Project Go never overwrites them: not on refresh, not on import of an AI
 * response, and not when a translated copy is installed. A full translated clone
 * keeps {@link #originalId()} and {@link #originalName()} exactly, because it is
 * the same mod rather than a new addon.</p>
 *
 * <p>User-visible derived names belong to {@link PresentationNames}. Internal
 * storage keys, digests, and fingerprints stay inside the workflow and must never
 * be derived from, or written back into, this record.</p>
 *
 * @param originalId exact id declared by the source mod
 * @param originalName exact display name declared by the source mod
 * @param originalFolderName name of the directory that holds the source mod
 * @param gameVersion declared Starsector game version, blank when undeclared
 */
public record SourceModIdentity(
        String originalId,
        String originalName,
        String originalFolderName,
        String gameVersion) {

    public SourceModIdentity {
        Objects.requireNonNull(originalId, "originalId must not be null");
        Objects.requireNonNull(originalName, "originalName must not be null");
        Objects.requireNonNull(originalFolderName, "originalFolderName must not be null");
        if (originalId.isBlank()) {
            throw new IllegalArgumentException("originalId must not be blank");
        }
        if (originalName.isBlank()) {
            throw new IllegalArgumentException("originalName must not be blank");
        }
        if (originalFolderName.isBlank()) {
            throw new IllegalArgumentException("originalFolderName must not be blank");
        }
        gameVersion = gameVersion == null ? "" : gameVersion;
    }
}
