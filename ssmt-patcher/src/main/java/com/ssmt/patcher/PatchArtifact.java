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
        this(relativePath, content, false);
    }

    private PatchArtifact(Path relativePath, byte[] content, boolean translatedMetadata) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Path normalized = relativePath.normalize();
        if (relativePath.isAbsolute()
                || normalized.getNameCount() == 0
                || normalized.startsWith("..")
                || !translatedMetadata && "mod_info.json".equalsIgnoreCase(normalized.toString())) {
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

    /** Explicit translated metadata retains every game-loading identity and dependency field. */
    public static PatchArtifact translatedModInfo(Path sourceRoot, byte[] content) throws java.io.IOException {
        var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_YAML_COMMENTS)
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES).build();
        var before = mapper.readTree(sourceRoot.resolve("mod_info.json").toFile());
        var after = mapper.readTree(content);
        if (before == null || after == null || !before.isObject() || !after.isObject()) {
            throw new java.io.IOException("Translated mod metadata is not an object");
        }
        for (String field : java.util.Set.of("id", "version", "gameVersion", "jars", "dependencies",
                "modPlugin", "utility", "totalConversion")) {
            if (!before.path(field).equals(after.path(field))) {
                throw new java.io.IOException("Translated metadata changed game-loading field " + field);
            }
        }
        return new PatchArtifact(Path.of("mod_info.json"), content, true);
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
