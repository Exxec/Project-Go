package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchRecoveryServiceTest {
    @TempDir Path directory;

    @Test void restoresOnlyAnUnchangedPreviousTreeWhenOutputIsMissing() throws Exception {
        Path output = directory.resolve("translated");
        Path previous = directory.resolve(".translated.ssmt-previous-translated");
        Files.createDirectories(previous);
        Files.writeString(previous.resolve("value.txt"), "last known good");
        var service = new PatchRecoveryService();
        var preview = service.inspect(output);

        assertThat(preview.action()).isEqualTo(PatchRecoveryService.Action.RESTORE_PREVIOUS);
        service.recover(preview);

        assertThat(Files.readString(output.resolve("value.txt"))).isEqualTo("last known good");
        assertThat(previous).doesNotExist();
    }

    @Test void refusesChangedOrAmbiguousRecoveryData() throws Exception {
        Path output = directory.resolve("translated");
        Path previous = directory.resolve(".translated.ssmt-previous-translated");
        Files.createDirectories(previous);
        Files.writeString(previous.resolve("value.txt"), "old");
        var service = new PatchRecoveryService();
        var preview = service.inspect(output);
        Files.writeString(previous.resolve("value.txt"), "changed");
        assertThatThrownBy(() -> service.recover(preview))
                .isInstanceOf(PatchBuilderException.class).hasMessageContaining("changed after preview");

        Files.createDirectories(output);
        assertThat(service.inspect(output).action())
                .isEqualTo(PatchRecoveryService.Action.REVIEW_REQUIRED);
    }

    @Test void requiresReviewWhenPreviousAndStagingTreesBothSurvive() throws Exception {
        Path output = directory.resolve("translated");
        Path previous = directory.resolve(".translated.ssmt-previous-translated");
        Path staging = directory.resolve(".translated.ssmt-translated-staging");
        Files.createDirectories(previous);
        Files.createDirectories(staging);
        Files.writeString(previous.resolve("value.txt"), "old");
        Files.writeString(staging.resolve("value.txt"), "new");

        var preview = new PatchRecoveryService().inspect(output);

        assertThat(preview.action()).isEqualTo(PatchRecoveryService.Action.REVIEW_REQUIRED);
        assertThat(previous).isDirectory();
        assertThat(staging).isDirectory();
    }
}
