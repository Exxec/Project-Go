package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageHygieneServiceTest {
    @TempDir Path directory;

    @Test void previewsSortedKnownCandidatesAndPreservesProjects() throws Exception {
        Path cache = Files.createDirectories(directory.resolve("projects/one/archive-source-abcdef"));
        Files.writeString(cache.resolve("mod_info.json"), "source");
        Path staging = directory.resolve("projects/two/project.json.ssmt-stage");
        Path archiveStaging = Files.createDirectories(
                directory.resolve("input-cache/archive-source-staging-crash"));
        Files.writeString(archiveStaging.resolve("partial.txt"), "partial");
        Files.createDirectories(Objects.requireNonNull(staging.getParent()));
        Files.writeString(staging, "staged");
        Path project = directory.resolve("projects/two/project.ssmt.json");
        Files.writeString(project, "durable");
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofDays(40)));
        Files.setLastModifiedTime(cache.resolve("mod_info.json"), old);
        Files.setLastModifiedTime(cache, old);
        Files.setLastModifiedTime(staging, old);
        Files.setLastModifiedTime(archiveStaging.resolve("partial.txt"), old);
        Files.setLastModifiedTime(archiveStaging, old);

        var service = new StorageHygieneService(directory);
        var preview = service.preview(Duration.ofDays(30));

        assertThat(preview.items()).extracting(StorageHygieneService.Item::relativePath)
                .containsExactly("input-cache/archive-source-staging-crash",
                        "projects/one/archive-source-abcdef",
                        "projects/two/project.json.ssmt-stage");
        assertThat(preview.files()).isEqualTo(3);
        service.cleanup(preview);
        assertThat(cache).doesNotExist();
        assertThat(staging).doesNotExist();
        assertThat(archiveStaging).doesNotExist();
        assertThat(project).isRegularFile();
    }

    @Test void preservesOldCacheWhenItContainsRecentWork() throws Exception {
        Path cache = Files.createDirectories(directory.resolve("projects/one/archive-source-abcdef"));
        Files.writeString(cache.resolve("recent.txt"), "new work");
        Files.setLastModifiedTime(cache, FileTime.from(Instant.EPOCH));

        var preview = new StorageHygieneService(directory).preview(Duration.ofDays(30));

        assertThat(preview.items()).isEmpty();
        assertThat(cache.resolve("recent.txt")).isRegularFile();
    }

    @Test void refusesCleanupWhenCandidateChangedAfterPreview() throws Exception {
        Path staging = directory.resolve("projects/project.json.ssmt-stage");
        Files.createDirectories(Objects.requireNonNull(staging.getParent()));
        Files.writeString(staging, "first");
        Files.setLastModifiedTime(staging, FileTime.from(Instant.EPOCH));
        var service = new StorageHygieneService(directory);
        var preview = service.preview(Duration.ZERO);
        Files.writeString(staging, "changed");

        assertThatThrownBy(() -> service.cleanup(preview))
                .isInstanceOf(ProjectException.class).hasMessageContaining("changed after preview");
        assertThat(staging).isRegularFile();
    }

    @Test void previewManifestRoundTripsAndCannotAuthorizeAProtectedPath() throws Exception {
        Path staging = directory.resolve("projects/project.json.ssmt-stage");
        Path durable = directory.resolve("projects/project.ssmt.json");
        Files.createDirectories(Objects.requireNonNull(staging.getParent()));
        Files.writeString(staging, "same bytes");
        Files.writeString(durable, "same bytes");
        Files.setLastModifiedTime(staging, FileTime.from(Instant.EPOCH));
        var service = new StorageHygieneService(directory);
        var preview = service.preview(Duration.ZERO);
        Path manifest = directory.resolve("preview.json");
        service.writePreview(manifest, preview);

        assertThat(service.readPreview(manifest)).isEqualTo(preview);
        var candidate = preview.items().getFirst();
        var forged = new StorageHygieneService.Preview(1, directory,
                java.util.List.of(new StorageHygieneService.Item("projects/project.ssmt.json",
                        candidate.kind(), candidate.bytes(), candidate.files(), candidate.treeHash())),
                candidate.bytes(), candidate.files());
        assertThatThrownBy(() -> service.cleanup(forged))
                .isInstanceOf(ProjectException.class).hasMessageContaining("protected path");
        assertThat(durable).isRegularFile();
    }
}
