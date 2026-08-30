package com.ssmt.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginCatalogTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversCompatiblePluginsDeterministicallyWithoutLoadingClasses() throws Exception {
        writePlugin("z.jar", "zeta", "1.0.0", 1);
        writePlugin("a.jar", "alpha", "2.0.0", 1);

        assertThat(new PluginCatalog().discover(temporaryDirectory))
                .extracting(PluginDescriptor::id)
                .containsExactly("alpha", "zeta");
    }

    @Test
    void rejectsUnsupportedApiAndDuplicateIds() throws Exception {
        writePlugin("future.jar", "future", "1.0.0", 2);
        assertThatThrownBy(() -> new PluginCatalog().discover(temporaryDirectory))
                .isInstanceOf(PluginCatalogException.class);

        Files.delete(temporaryDirectory.resolve("future.jar"));
        writePlugin("one.jar", "same", "1.0.0", 1);
        writePlugin("two.jar", "same", "2.0.0", 1);
        assertThatThrownBy(() -> new PluginCatalog().discover(temporaryDirectory))
                .isInstanceOf(PluginCatalogException.class);
    }

    @Test
    void ignoresNonJarFilesAndRejectsMalformedMetadata() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "ignored");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(temporaryDirectory.resolve("bad.jar")))) {
            jar.putNextEntry(new JarEntry("META-INF/ssmt-plugin.json"));
            jar.write("{bad".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        assertThatThrownBy(() -> new PluginCatalog().discover(temporaryDirectory))
                .isInstanceOf(PluginCatalogException.class);
    }

    private void writePlugin(String file, String id, String version, int apiVersion)
            throws IOException {
        try (JarOutputStream jar =
                new JarOutputStream(Files.newOutputStream(temporaryDirectory.resolve(file)))) {
            jar.putNextEntry(new JarEntry("META-INF/ssmt-plugin.json"));
            String metadata = "{\"id\":\"" + id
                    + "\",\"name\":\"" + id
                    + "\",\"version\":\"" + version
                    + "\",\"apiVersion\":" + apiVersion
                    + ",\"providerClass\":\"example.Plugin\"}";
            jar.write(metadata.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("example/Plugin.class"));
            jar.write(new byte[] {0, 1, 2});
            jar.closeEntry();
        }
    }
}
