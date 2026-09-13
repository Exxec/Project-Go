package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CandidateInventoryTest {
    @TempDir Path directory;

    @Test void inventoryIsSortedRepeatableAndReadOnly() throws Exception {
        Path root = Files.createDirectory(directory.resolve("candidate"));
        Path nested = Files.createDirectory(root.resolve("nested"));
        Path source = nested.resolve("z.java");
        Files.writeString(source, "class Example {}\n");
        Files.writeString(root.resolve("a.csv"), "id,name\na,Hello\n");
        var modified = Files.getLastModifiedTime(source);
        var inventory = new CandidateInventory();
        var entries = inventory.capture(root);
        assertThat(entries).isEqualTo(inventory.capture(root));
        assertThat(entries).extracting(CandidateInventory.Entry::path)
                .containsExactly("a.csv", "nested/z.java");
        assertThat(entries.get(1).kind()).isEqualTo("JAVA_SOURCE");
        assertThat(entries.get(1).sha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        assertThat(Files.getLastModifiedTime(source)).isEqualTo(modified);
        assertThat(Files.readString(source)).isEqualTo("class Example {}\n");
    }

    @Test void rejectsFilesAsDirectoryInputsWithoutChangingThem() throws Exception {
        Path file = directory.resolve("candidate.zip");
        Files.writeString(file, "unchanged");
        assertThatThrownBy(() -> new CandidateInventory().capture(file))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("directory");
        assertThat(Files.readString(file)).isEqualTo("unchanged");
    }
}
