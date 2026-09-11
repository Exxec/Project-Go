package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.patcher.PatchRecoveryService;
import com.ssmt.project.StorageHygieneService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowMaintenanceControllerTest {
    @TempDir Path directory;

    @Test void cleanupUsesAnExactPreviewAndRecoveryRequiresAnUnambiguousPreviousCopy()
            throws Exception {
        Path cache = directory.resolve("data/archive-source-old");
        Files.createDirectories(cache);
        Files.writeString(cache.resolve("file.txt"), "cache");
        FileTime old = FileTime.from(Instant.now().minus(java.time.Duration.ofDays(40)));
        Files.setLastModifiedTime(cache.resolve("file.txt"), old);
        Files.setLastModifiedTime(cache, old);
        var controller = new WorkflowMaintenanceController(
                new StorageHygieneService(directory.resolve("data")), new PatchRecoveryService());

        var cleanup = controller.previewCleanup(Optional.empty());
        assertThat(cleanup.items()).hasSize(1);
        controller.cleanup(cleanup);
        assertThat(cache).doesNotExist();

        Path output = directory.resolve("translated");
        Path previous = directory.resolve(".translated.ssmt-previous-translated");
        Files.createDirectories(previous);
        Files.writeString(previous.resolve("mod_info.json"), "{}");
        var recovery = controller.inspectRecovery(output);
        assertThat(recovery.action()).isEqualTo(PatchRecoveryService.Action.RESTORE_PREVIOUS);
        controller.recover(recovery);
        assertThat(output.resolve("mod_info.json")).exists();
        assertThat(previous).doesNotExist();
    }

    @Test void activeExtractedSourceIsExcludedFromCleanupPreview() throws Exception {
        Path cache = directory.resolve("data/archive-source-active");
        Path source = cache.resolve("wrapped");
        Files.createDirectories(source);
        Files.writeString(source.resolve("mod_info.json"), "{}");
        FileTime old = FileTime.from(Instant.now().minus(java.time.Duration.ofDays(40)));
        Files.setLastModifiedTime(source.resolve("mod_info.json"), old);
        Files.setLastModifiedTime(source, old);
        Files.setLastModifiedTime(cache, old);
        var controller = new WorkflowMaintenanceController(
                new StorageHygieneService(directory.resolve("data")), new PatchRecoveryService());

        assertThat(controller.previewCleanup(Optional.of(source)).items()).isEmpty();
        assertThat(source.resolve("mod_info.json")).exists();
    }
}
