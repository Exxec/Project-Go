package com.ssmt.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginActivatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesCompatiblePluginInWorkerProcess() throws Exception {
        Path archive = compilePlugin("example.WorkingPlugin", """
                package example;
                import com.ssmt.core.plugin.PluginContext;
                import com.ssmt.core.plugin.SsmtPlugin;
                import com.ssmt.core.exception.PluginLoadException;
                public final class WorkingPlugin implements SsmtPlugin {
                    public void initialize(PluginContext context) throws PluginLoadException {
                        System.out.print("plugin noise");
                    }
                    public String getPluginId() { return "working"; }
                    public String getVersion() { return "1.0.0"; }
                }
                """);
        PluginDescriptor descriptor =
                new PluginDescriptor("working", "Working", "1.0.0", 1, "example.WorkingPlugin", archive);
        Path work = temporaryDirectory.resolve("work");
        Files.createDirectories(work);

        PluginActivationResult result =
                new PluginActivator().activate(descriptor, work, Duration.ofSeconds(10));

        assertThat(result.id()).isEqualTo("working");
        assertThat(result.version()).isEqualTo("1.0.0");
    }

    @Test
    void reportsMissingProviderWithoutAffectingCaller() throws Exception {
        Path archive = temporaryDirectory.resolve("empty.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("placeholder"));
            jar.closeEntry();
        }
        PluginDescriptor descriptor =
                new PluginDescriptor("missing", "Missing", "1.0.0", 1, "missing.Plugin", archive);
        Path work = temporaryDirectory.resolve("work");
        Files.createDirectories(work);

        assertThatThrownBy(() ->
                        new PluginActivator().activate(descriptor, work, Duration.ofSeconds(10)))
                .isInstanceOf(PluginActivationException.class);
    }

    @Test
    void terminatesWorkerAtConfiguredTimeout() throws Exception {
        Path archive = compilePlugin("example.SlowPlugin", """
                package example;
                import com.ssmt.core.plugin.PluginContext;
                import com.ssmt.core.plugin.SsmtPlugin;
                import com.ssmt.core.exception.PluginLoadException;
                public final class SlowPlugin implements SsmtPlugin {
                    public void initialize(PluginContext context) throws PluginLoadException {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    public String getPluginId() { return "slow"; }
                    public String getVersion() { return "1.0.0"; }
                }
                """);
        PluginDescriptor descriptor =
                new PluginDescriptor("slow", "Slow", "1.0.0", 1, "example.SlowPlugin", archive);
        Path work = temporaryDirectory.resolve("slow-work");
        Files.createDirectories(work);

        assertThatThrownBy(() ->
                        new PluginActivator().activate(
                                descriptor, work, Duration.ofMillis(100)))
                .isInstanceOf(PluginActivationException.class)
                .hasMessageContaining("timed out");
    }

    private Path compilePlugin(String className, String source) throws IOException {
        Path sourceRoot = temporaryDirectory.resolve("source");
        Path classes = temporaryDirectory.resolve("classes");
        Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(Objects.requireNonNull(sourceFile.getParent()));
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(
                null,
                null,
                null,
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                classes.toString(),
                sourceFile.toString());
        if (result != 0) {
            throw new IOException("Test plugin compilation failed");
        }
        Path archive = temporaryDirectory.resolve("plugin.jar");
        Path classFile = classes.resolve(className.replace('.', '/') + ".class");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry(className.replace('.', '/') + ".class"));
            jar.write(Files.readAllBytes(classFile));
            jar.closeEntry();
        }
        return archive.toAbsolutePath();
    }
}
