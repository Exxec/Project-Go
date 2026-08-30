package com.ssmt.patcher;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validated patch assembly request.
 *
 * @param sourceRoot source mod root, never written
 * @param outputRoot translated clone destination; pristine clone is a deterministic sibling
 * @param patchId project translation identifier retained for fingerprint compatibility
 * @param patchName project translation name retained for fingerprint compatibility
 * @param sourceModId source mod id
 * @param sourceModName source mod's own declared display name
 * @param sourceGameVersion source mod's declared Starsector game version, or blank/null if undeclared
 * @param artifacts complete output artifacts
 */
public record PatchRequest(
        Path sourceRoot,
        Path outputRoot,
        String patchId,
        String patchName,
        String sourceModId,
        String sourceModName,
        String sourceGameVersion,
        List<PatchArtifact> artifacts) {

    /**
     * Normalizes paths and enforces containment and uniqueness.
     */
    public PatchRequest {
        sourceRoot = absolute(sourceRoot, "sourceRoot");
        outputRoot = absolute(outputRoot, "outputRoot");
        requireText(patchId, "patchId");
        requireText(patchName, "patchName");
        requireText(sourceModId, "sourceModId");
        requireText(sourceModName, "sourceModName");
        if (sourceRoot.startsWith(outputRoot) || outputRoot.startsWith(sourceRoot)) {
            throw new IllegalArgumentException("Source and output roots must not overlap");
        }
        Path sourceBackup = PatchBuilder.sourceBackupRoot(outputRoot);
        if (sourceRoot.startsWith(sourceBackup) || sourceBackup.startsWith(sourceRoot)) {
            throw new IllegalArgumentException("Source and pristine clone roots must not overlap");
        }
        artifacts = List.copyOf(artifacts).stream()
                .sorted(Comparator.comparing(item -> item.relativePath().toString()))
                .toList();
        Set<Path> paths = new HashSet<>();
        for (PatchArtifact artifact : artifacts) {
            if (!paths.add(artifact.relativePath())) {
                throw new IllegalArgumentException(
                        "Duplicate artifact path " + artifact.relativePath());
            }
        }
    }

    @Override
    public List<PatchArtifact> artifacts() {
        return List.copyOf(artifacts);
    }

    private static Path absolute(Path value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value.toAbsolutePath().normalize();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
