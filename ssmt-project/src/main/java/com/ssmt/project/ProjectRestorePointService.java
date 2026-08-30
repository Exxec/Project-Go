package com.ssmt.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Stores one explicit, user-created restore point beside a project document. */
public final class ProjectRestorePointService {
    private static final String DIRECTORY = ".project-go-restore";
    private static final String FILE = "last-restore-point.json";
    private final LocalizationProjectService projects = new LocalizationProjectService();

    /** Writes the current project state as the point to which the user may undo. */
    public Path create(Path projectFile, LocalizationProject project) throws ProjectException {
        Path destination = root(projectFile).resolve(FILE);
        try {
            Files.createDirectories(destination.getParent());
        } catch (java.io.IOException exception) {
            throw new ProjectException("Could not create restore-point directory", exception);
        }
        projects.write(destination, project);
        return destination;
    }

    /** @return the latest explicit restore point, when one exists */
    public Optional<LocalizationProject> load(Path projectFile) throws ProjectException {
        Path candidate = root(projectFile).resolve(FILE);
        return Files.isRegularFile(candidate) ? Optional.of(projects.read(candidate)) : Optional.empty();
    }

    private static Path root(Path projectFile) {
        Path parent = projectFile.toAbsolutePath().normalize().getParent();
        return (parent == null ? Path.of(".").toAbsolutePath().normalize() : parent)
                .resolve(DIRECTORY);
    }
}
