package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveInventoryTest {
    @TempDir Path directory;

    private Path zip(String... names) throws Exception {
        Path archive = directory.resolve("candidate.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : names) {
                output.putNextEntry(new ZipEntry(name));
                output.write("content".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    @Test void repeatableSortedInventoryDoesNotExtractOrChangeArchive() throws Exception {
        Path archive = zip("wrapper/z.txt", "wrapper/mod_info.json");
        byte[] before = Files.readAllBytes(archive);
        var modified = Files.getLastModifiedTime(archive);
        var inventory = new ArchiveInventory();
        var entries = inventory.capture(archive);
        assertThat(entries).isEqualTo(inventory.capture(archive));
        assertThat(entries).extracting(ArchiveInventory.Entry::path)
                .containsExactly("wrapper/mod_info.json", "wrapper/z.txt");
        assertThat(entries.getFirst().bytes()).isEqualTo(7);
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(archive)).isEqualTo(modified);
        assertThat(directory.resolve("wrapper")).doesNotExist();
    }

    @Test void rejectsTraversalWithoutExtraction() throws Exception {
        Path archive = zip("../outside.txt");
        assertThatThrownBy(() -> new ArchiveInventory().capture(archive))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("Unsafe");
        assertThat(directory.getParent().resolve("outside.txt")).doesNotExist();
    }

    @Test void rejectsCaseCollisions() throws Exception {
        Path archive = zip("a.txt", "A.txt");
        assertThatThrownBy(() -> new ArchiveInventory().capture(archive))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("colliding");
    }

    @Test void namesTheEntryWithAnInvalidCrcWithoutChangingTheArchive() throws Exception {
        Path archive = directory.resolve("bad-crc.zip");
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        var crc = new java.util.zip.CRC32();
        crc.update(content);
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            var entry = new ZipEntry("payload.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(content);
            output.closeEntry();
        }
        byte[] corrupted = Files.readAllBytes(archive);
        java.util.Arrays.fill(corrupted, 14, 18, (byte) 0);
        Files.write(archive, corrupted);

        assertThatThrownBy(() -> new ArchiveInventory().capture(archive))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("payload.txt").hasMessageContaining("CRC");
        assertThat(Files.readAllBytes(archive)).isEqualTo(corrupted);
    }
}
