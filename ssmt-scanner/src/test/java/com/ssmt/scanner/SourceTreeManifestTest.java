package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceTreeManifestTest {
    @TempDir Path root;

    @Test void capturesEmptyDirectoriesAndMetadataWithoutChangingInputs() throws Exception {
        Files.createDirectory(root.resolve("empty"));
        Path file = root.resolve("source.txt");
        Files.writeString(file, "source");
        var modified = Files.getLastModifiedTime(file);
        var manifest = new SourceTreeManifest();
        var first = manifest.capture(root);
        assertThat(first).isEqualTo(manifest.capture(root));
        assertThat(first).extracting(SourceTreeManifest.Node::path).containsExactly("", "empty", "source.txt");
        assertThat(first.get(1).kind()).isEqualTo("DIRECTORY");
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(modified);
        assertThat(Files.readString(file)).isEqualTo("source");
    }

    @Test void metadataChangeIsDistinctFromPortableContentIdentity() throws Exception {
        Path file = root.resolve("source.txt");
        Files.writeString(file, "source");
        var inventory = new CandidateInventory().capture(root);
        var manifest = new SourceTreeManifest();
        var before = manifest.capture(root);
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() - 60_000));
        assertThat(manifest.capture(root)).isNotEqualTo(before);
        assertThat(new CandidateInventory().capture(root)).isEqualTo(inventory);
    }
}
