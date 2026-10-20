package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModInputPreparationServiceTest {
    @TempDir Path directory;

    @Test void invalidAncestorFolderExplainsModRootBeforeCacheOverlap() throws Exception {
        Path desktop = Files.createDirectory(directory.resolve("Desktop"));
        Path mod = Files.createDirectory(desktop.resolve("actual-mod"));
        Path metadata = Files.writeString(mod.resolve("mod_info.json"), "{}");
        Path cache = desktop.resolve("application/input-cache");
        assertThatThrownBy(() -> new ModInputPreparationService().prepare(desktop, cache))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("mod's own folder")
                .hasMessageContaining("ZIP archive")
                .hasMessageNotContaining("cache must be outside");
        assertThat(cache).doesNotExist();
        assertThat(metadata).hasContent("{}");
    }

    @Test void acceptsDirectoryAndMetadataFileWithoutCopyingEither() throws Exception {
        Path source = directory.resolve("source");
        Files.createDirectories(source);
        Path metadata = Files.writeString(source.resolve("mod_info.json"), "{}");
        var service = new ModInputPreparationService();

        var fromDirectory = service.prepare(source, directory.resolve("cache"));
        var fromMetadata = service.prepare(metadata, directory.resolve("cache"));

        assertThat(fromDirectory.kind()).isEqualTo(
                ModInputPreparationService.InputKind.DIRECTORY);
        assertThat(fromMetadata.kind()).isEqualTo(
                ModInputPreparationService.InputKind.MOD_INFO_FILE);
        assertThat(fromDirectory.modRoot()).isEqualTo(source.toRealPath());
        assertThat(fromMetadata.modRoot()).isEqualTo(source.toRealPath());
        assertThat(fromDirectory.archiveSha256()).isEmpty();
        assertThat(Files.readString(metadata)).isEqualTo("{}");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test void extractsNestedArchiveIntoHashBoundCacheAndPreservesArchive() throws Exception {
        Path archive = zip("mod.zip", Map.of(
                "wrapper/mod/mod_info.json", "{}",
                "wrapper/mod/data/strings.json", "hello"));
        byte[] before = Files.readAllBytes(archive);
        Path cache = directory.resolve("cache");
        var service = new ModInputPreparationService();

        var prepared = service.prepare(archive, cache);
        var repeated = service.prepare(archive, cache);

        assertThat(prepared.kind()).isEqualTo(
                ModInputPreparationService.InputKind.ZIP_ARCHIVE);
        assertThat(prepared.originalInput()).isEqualTo(archive.toRealPath());
        assertThat(prepared.archiveSha256()).hasValueSatisfying(hash ->
                assertThat(hash).hasSize(64));
        assertThat(prepared.modRoot()).isEqualTo(repeated.modRoot()).isDirectory();
        assertThat(prepared.modRoot()).startsWith(cache.toRealPath());
        assertThat(prepared.modRoot().resolve("data/strings.json")).hasContent("hello");
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        try (var children = Files.list(cache)) {
            assertThat(children.filter(Files::isDirectory)).hasSize(1);
        }
    }

    @Test void rejectsTraversalAndRemovesPartialExtraction() throws Exception {
        Path archive = zip("unsafe.zip", Map.of(
                "mod/mod_info.json", "{}",
                "../escaped.txt", "escape"));
        Path cache = directory.resolve("cache");

        assertThatThrownBy(() -> new ModInputPreparationService().prepare(archive, cache))
                .isInstanceOf(ProjectException.class).hasMessageContaining("unsafe file path");
        assertThat(directory.resolve("escaped.txt")).doesNotExist();
        try (var children = Files.list(cache)) {
            assertThat(children).isEmpty();
        }
    }

    @Test void refusesChangedMissingAndExtraCachedFilesWithoutDeletingWork() throws Exception {
        Path archive = zip("cache-check.zip", Map.of(
                "mod/mod_info.json", "{}", "mod/text.txt", "original"));
        byte[] originalArchive = Files.readAllBytes(archive);
        var service = new ModInputPreparationService();
        Path cache = directory.resolve("cache-check");
        Path root = service.prepare(archive, cache).modRoot();
        Path text = root.resolve("text.txt");
        Files.writeString(text, "modified");
        assertThatThrownBy(() -> service.prepare(archive, cache))
                .isInstanceOf(ProjectException.class).hasMessageContaining("cached archive differs");
        assertThat(text).hasContent("modified");
        Files.delete(text);
        assertThatThrownBy(() -> service.prepare(archive, cache))
                .isInstanceOf(ProjectException.class).hasMessageContaining("cached archive differs");
        Files.writeString(text, "original");
        Path extra = Files.writeString(java.util.Objects.requireNonNull(root.getParent()).resolve("extra.txt"),
                "outside selected root");
        assertThatThrownBy(() -> service.prepare(archive, cache))
                .isInstanceOf(ProjectException.class).hasMessageContaining("cached archive differs");
        assertThat(extra).hasContent("outside selected root");
        Files.delete(extra);
        assertThat(service.prepare(archive, cache).modRoot()).isEqualTo(root);
        assertThat(Files.readAllBytes(archive)).isEqualTo(originalArchive);
        try (var children = Files.list(cache)) {
            assertThat(children).hasSize(1);
        }
    }

    @Test void cacheReuseDoesNotBypassCurrentArchiveLimits() throws Exception {
        Path archive = zip("bounded-cache.zip", Map.of("mod_info.json", "{}", "text.txt", "original"));
        Path cache = directory.resolve("bounded-cache");
        new ModInputPreparationService().prepare(archive, cache);
        assertThatThrownBy(() -> new ModInputPreparationService(1, 100).prepare(archive, cache))
                .isInstanceOf(ProjectException.class).hasMessageContaining("too many entries");
        assertThatThrownBy(() -> new ModInputPreparationService(10, 3).prepare(archive, cache))
                .isInstanceOf(ProjectException.class).hasMessageContaining("safety limit");
    }

    @Test void requiresExactlyOneArchiveMetadataFile() throws Exception {
        Path missing = zip("missing.zip", Map.of("readme.txt", "none"));
        Path ambiguous = zip("ambiguous.zip", Map.of(
                "one/mod_info.json", "{}", "two/mod_info.json", "{}"));
        var service = new ModInputPreparationService();

        assertThatThrownBy(() -> service.prepare(missing, directory.resolve("missing-cache")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> service.prepare(ambiguous, directory.resolve("ambiguous-cache")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("exactly one");
    }

    @Test void enforcesEntryAndExpandedByteLimits() throws Exception {
        Path entries = zip("entries.zip", Map.of(
                "mod_info.json", "{}", "extra.txt", "x"));
        Path bytes = zip("bytes.zip", Map.of("mod_info.json", "12345"));

        assertThatThrownBy(() -> new ModInputPreparationService(1, 100)
                .prepare(entries, directory.resolve("entry-cache")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("too many entries");
        assertThatThrownBy(() -> new ModInputPreparationService(10, 4)
                .prepare(bytes, directory.resolve("byte-cache")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("safety limit");
    }

    @Test void rejectsCacheInsideDirectSourceAndUnsupportedFiles() throws Exception {
        Path source = directory.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("mod_info.json"), "{}");
        Path text = Files.writeString(directory.resolve("mod.txt"), "not a mod");
        var service = new ModInputPreparationService();

        assertThatThrownBy(() -> service.prepare(source, source.resolve("cache")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("outside");
        assertThatThrownBy(() -> service.prepare(text, directory.resolve("cache")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("ZIP archive");
    }

    private Path zip(String name, Map<String, String> entries) throws Exception {
        Path archive = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }
}
