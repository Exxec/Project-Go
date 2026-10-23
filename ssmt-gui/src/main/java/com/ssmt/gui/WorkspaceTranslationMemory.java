package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import com.ssmt.tm.TranslationMemoryMergeResult;
import com.ssmt.tm.TranslationMemoryMergeService;
import java.nio.file.Path;

/** Catalog verification, creation and conservative translation-memory merges. */
final class WorkspaceTranslationMemory {
    private final TranslationMemoryMergeService memoryMerge =
            new TranslationMemoryMergeService();

    void verifyTranslationMemory(Path database) throws ProjectException {
        if (!java.nio.file.Files.isRegularFile(database)) {
            throw new ProjectException(
                    "Translation-memory database does not exist: " + database);
        }
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(database)) {
            if (!memory.verifyIntegrity()) {
                throw new ProjectException(
                        "Translation-memory integrity check failed: " + database);
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not open translation-memory database", exception);
        }
    }

    void openOrCreateTranslationMemory(Path database) throws ProjectException {
        Path parent = database.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            try {
                java.nio.file.Files.createDirectories(parent);
            } catch (java.io.IOException exception) {
                throw new ProjectException(
                        "Could not create translation-memory directory: " + parent, exception);
            }
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            if (!memory.verifyIntegrity()) {
                throw new ProjectException(
                        "Translation-memory integrity check failed: " + database);
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not open or create translation-memory database", exception);
        }
    }

    TranslationMemoryMergeResult compareTranslationMemories(
            Path source,
            Path destination) throws ProjectException {
        try {
            return memoryMerge.compare(source, destination);
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not compare translation memories: " + exception.getMessage(),
                    exception);
        }
    }

    TranslationMemoryMergeResult mergeTranslationMemories(
            Path source,
            Path destination) throws ProjectException {
        try {
            return memoryMerge.merge(source, destination);
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not merge translation memories: " + exception.getMessage(),
                    exception);
        }
    }
}
