package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Source-bound durable checkpoints stored beside user-owned project artifacts. */
public final class ProjectTranslationCheckpointService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LocalizationProjectService projects = new LocalizationProjectService();

    public void save(Path checkpoint, LocalizationProject project, int completedEntries)
            throws ProjectException {
        Path projectState = statePath(checkpoint);
        projects.write(projectState, project);
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("sourceModId", project.sourceModId());
        manifest.put("sourceDigest", sourceDigest(project));
        manifest.put("completedEntries", completedEntries);
        manifest.put("projectState", Objects.requireNonNull(projectState.getFileName()).toString());
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(checkpoint.toFile(), manifest);
        } catch (IOException exception) {
            throw new ProjectException("Could not save translation checkpoint", exception);
        }
    }

    public LocalizationProject resume(Path checkpoint, LocalizationProject current)
            throws ProjectException {
        try {
            var manifest = JSON.readTree(checkpoint.toFile());
            if (!current.sourceModId().equals(manifest.path("sourceModId").asText())
                    || !sourceDigest(current).equals(manifest.path("sourceDigest").asText())) {
                throw new ProjectException(
                        "Translation checkpoint does not match the current source project");
            }
            LocalizationProject saved = projects.read(statePath(checkpoint));
            if (!sourceDigest(saved).equals(sourceDigest(current))) {
                throw new ProjectException("Translation checkpoint state is inconsistent");
            }
            return saved;
        } catch (IOException exception) {
            throw new ProjectException("Could not read translation checkpoint", exception);
        }
    }

    private static Path statePath(Path checkpoint) {
        return checkpoint.resolveSibling(
                Objects.requireNonNull(checkpoint.getFileName()) + ".project.json");
    }

    private static String sourceDigest(LocalizationProject project) throws ProjectException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(project.sourceModId().getBytes(StandardCharsets.UTF_8));
            for (ProjectEntry entry : project.entries()) {
                digest.update(entry.sourceFile().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.key().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.originalText().getBytes(StandardCharsets.UTF_8));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new ProjectException("SHA-256 is unavailable", exception);
        }
    }
}
