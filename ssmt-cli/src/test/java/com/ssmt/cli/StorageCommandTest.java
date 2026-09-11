package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.project.StorageHygieneService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StorageCommandTest {
    @TempDir Path directory;

    @Test void previewPreservesAndCleanRemovesOnlyListedStaging() throws Exception {
        Path staging = directory.resolve("projects/project.json.ssmt-stage");
        Path durable = directory.resolve("projects/project.ssmt.json");
        Files.createDirectories(Objects.requireNonNull(staging.getParent()));
        Files.writeString(staging, "temporary");
        Files.writeString(durable, "durable");
        Files.setLastModifiedTime(staging, FileTime.from(Instant.EPOCH));
        Path manifest = directory.resolve("cleanup-preview.json");
        var service = new StorageHygieneService(directory);

        int preview = new CommandLine(new StorageCommand(service, manifest)).execute(
                "preview", "--older-than-days", "1");
        assertThat(preview).isZero();
        assertThat(staging).isRegularFile();
        assertThat(manifest).isRegularFile();

        Path later = directory.resolve("projects/later.ssmt-stage");
        Files.writeString(later, "not approved");
        Files.setLastModifiedTime(later, FileTime.from(Instant.EPOCH));

        int clean = new CommandLine(new StorageCommand(service, manifest)).execute("clean");
        assertThat(clean).isZero();
        assertThat(staging).doesNotExist();
        assertThat(later).isRegularFile();
        assertThat(durable).isRegularFile();
    }
}
