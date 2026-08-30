package com.ssmt.gui;

import java.nio.file.Path;
import java.util.Optional;

/** Read-only locations associated with the active GUI workspace. */
public record ProjectWorkspaceInfo(
        Optional<Path> sourceRoot,
        Optional<Path> projectFile,
        Optional<Path> outputRoot,
        Optional<Path> sourceBackupRoot,
        Optional<Path> translationMemory,
        Optional<Path> recoveryRoot,
        Optional<Path> jsonSchemaCatalog,
        Optional<Path> csvSchemaCatalog) {
}
