package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarContentsTest {
    @TempDir Path root;

    private Path jar() throws Exception {
        Path relative = Path.of("mod.jar");
        try (var output = new ZipOutputStream(Files.newOutputStream(root.resolve(relative)))) {
            for (String name : java.util.List.of("Example.class", "Example.java", "resource.txt")) {
                output.putNextEntry(new ZipEntry(name));
                output.write("unverified payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return relative;
    }

    @Test void inventoriesClassSourceAndResourcesWithoutLoadingClassesOrAssumingEquivalence() throws Exception {
        Path relative = jar();
        byte[] before = Files.readAllBytes(root.resolve(relative));
        String hash = InventoryFingerprint.archive(root.resolve(relative));
        var report = JarContents.inspect(root, relative, hash);
        assertThat(report.sourceJarCorrespondence()).isEqualTo("NOT_ESTABLISHED");
        assertThat(report.entries()).extracting(JarContents.Entry::category)
                .containsExactly("CLASS_ENTRY_UNVERIFIED", "BUNDLED_SOURCE", "RESOURCE");
        assertThat(report).isEqualTo(JarContents.inspect(root, relative, hash));
        assertThat(Files.readAllBytes(root.resolve(relative))).isEqualTo(before);
    }

    @Test void rejectsForeignHashAndEscapingPath() throws Exception {
        Path relative = jar();
        assertThatThrownBy(() -> JarContents.inspect(root, relative, "a".repeat(64)))
                .hasMessageContaining("changed after");
        assertThatThrownBy(() -> JarContents.inspect(root, Path.of("../outside.jar"), "a".repeat(64)))
                .hasMessageContaining("beneath");
    }
}
