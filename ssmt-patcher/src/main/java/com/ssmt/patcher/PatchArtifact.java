package com.ssmt.patcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Complete content for one relative output file.
 */
public final class PatchArtifact {
    private final Path relativePath;
    private final byte[] content;

    /**
     * Creates a defensively copied artifact.
     *
     * @param relativePath normalized relative output path
     * @param content complete file bytes
     */
    public PatchArtifact(Path relativePath, byte[] content) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Path normalized = relativePath.normalize();
        if (relativePath.isAbsolute()
                || normalized.getNameCount() == 0
                || normalized.startsWith("..")
                || "mod_info.json".equalsIgnoreCase(normalized.toString())) {
            throw new IllegalArgumentException("Artifact path must be a safe relative data path");
        }
        this.relativePath = normalized;
        this.content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    /**
     * Creates a UTF-8 text artifact.
     *
     * @param relativePath output path
     * @param content text content
     * @return artifact
     */
    public static PatchArtifact utf8(Path relativePath, String content) {
        return new PatchArtifact(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    public Path relativePath() {
        return relativePath;
    }

    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PatchArtifact artifact
                && relativePath.equals(artifact.relativePath)
                && Arrays.equals(content, artifact.content);
    }

    @Override
    public int hashCode() {
        return 31 * relativePath.hashCode() + Arrays.hashCode(content);
    }
}
