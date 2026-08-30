package com.ssmt.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Stable, source-external project crash-recovery snapshots.
 */
public final class ProjectRecoveryService {
    private final LocalizationProjectService projects = new LocalizationProjectService();

    /**
     * Writes or replaces the stable recovery snapshot for a project document.
     *
     * @param recoveryRoot SSMT-owned recovery directory
     * @param projectFile project document identity
     * @param project current in-memory project
     * @return snapshot path
     * @throws ProjectException on directory or write failure
     */
    public Path snapshot(
            Path recoveryRoot,
            Path projectFile,
            LocalizationProject project) throws ProjectException {
        Path root = recoveryRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (java.io.IOException exception) {
            throw new ProjectException("Could not create recovery directory", exception);
        }
        Path snapshot = root.resolve(identity(projectFile) + ".ssmt-recovery.json");
        projects.write(snapshot, project);
        return snapshot;
    }

    /**
     * Reads a recovery snapshot when present.
     *
     * @param recoveryRoot SSMT-owned recovery directory
     * @param projectFile project document identity
     * @return recovered project when available
     * @throws ProjectException on malformed snapshot
     */
    public Optional<LocalizationProject> recover(
            Path recoveryRoot,
            Path projectFile) throws ProjectException {
        Path snapshot = recoveryRoot.toAbsolutePath().normalize()
                .resolve(identity(projectFile) + ".ssmt-recovery.json");
        return Files.isRegularFile(snapshot)
                ? Optional.of(projects.read(snapshot))
                : Optional.empty();
    }

    private static String identity(Path projectFile) throws ProjectException {
        try {
            byte[] bytes = projectFile.toAbsolutePath().normalize().toString()
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new ProjectException("SHA-256 is unavailable", exception);
        }
    }
}
