package com.ssmt.gui;

import com.ssmt.patcher.PatchBuilderException;
import com.ssmt.patcher.PatchRecoveryService;
import com.ssmt.project.ProjectException;
import com.ssmt.project.StorageHygieneService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Hash-bound maintenance operations exposed by the normal-workflow settings dialog. */
final class WorkflowMaintenanceController {
    private static final Duration DEFAULT_CLEANUP_AGE = Duration.ofDays(30);
    private final StorageHygieneService storage;
    private final PatchRecoveryService recovery;

    WorkflowMaintenanceController() {
        this(new StorageHygieneService(), new PatchRecoveryService());
    }

    WorkflowMaintenanceController(StorageHygieneService storage, PatchRecoveryService recovery) {
        this.storage = storage;
        this.recovery = recovery;
    }

    StorageHygieneService.Preview previewCleanup(Optional<Path> protectedSource)
            throws ProjectException {
        StorageHygieneService.Preview preview = storage.preview(DEFAULT_CLEANUP_AGE);
        if (protectedSource.isEmpty()) {
            return preview;
        }
        Path protectedPath = protectedSource.orElseThrow().toAbsolutePath().normalize();
        var items = preview.items().stream().filter(item -> !protectedPath.startsWith(
                preview.root().resolve(item.relativePath()).normalize())).toList();
        return new StorageHygieneService.Preview(preview.schemaVersion(), preview.root(), items,
                items.stream().mapToLong(StorageHygieneService.Item::bytes).sum(),
                items.stream().mapToInt(StorageHygieneService.Item::files).sum());
    }

    void cleanup(StorageHygieneService.Preview approved) throws ProjectException {
        storage.cleanup(approved);
    }

    PatchRecoveryService.Preview inspectRecovery(Path output) throws PatchBuilderException {
        return recovery.inspect(output);
    }

    void recover(PatchRecoveryService.Preview approved) throws PatchBuilderException {
        recovery.recover(approved);
    }
}
