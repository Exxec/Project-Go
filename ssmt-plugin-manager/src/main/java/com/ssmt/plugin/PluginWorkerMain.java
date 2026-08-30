package com.ssmt.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.plugin.PluginContext;
import com.ssmt.core.plugin.SsmtPlugin;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Internal child-process entry point for isolated plugin initialization.
 */
public final class PluginWorkerMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PluginWorkerMain() {
    }

    /**
     * Executes one plugin lifecycle initialization and writes one JSON result.
     *
     * @param args archive, provider, expected id/version, and working directory
     */
    public static void main(String[] args) {
        PrintStream protocol = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        ObjectNode response = MAPPER.createObjectNode();
        int exitCode = 0;
        try {
            if (args.length != 5) {
                throw new IllegalArgumentException("Expected five worker arguments");
            }
            Path archive = Path.of(args[0]).toAbsolutePath().normalize();
            Path working = Path.of(args[4]).toAbsolutePath().normalize();
            try (URLClassLoader loader = new URLClassLoader(
                    new URL[] {archive.toUri().toURL()},
                    PluginWorkerMain.class.getClassLoader())) {
                Class<?> provider = Class.forName(args[1], true, loader);
                if (!SsmtPlugin.class.isAssignableFrom(provider)) {
                    throw new IllegalArgumentException("Provider does not implement SsmtPlugin");
                }
                SsmtPlugin plugin =
                        (SsmtPlugin) provider.getDeclaredConstructor().newInstance();
                if (!args[2].equals(plugin.getPluginId())
                        || !args[3].equals(plugin.getVersion())) {
                    throw new IllegalArgumentException("Provider identity differs from manifest");
                }
                plugin.initialize(new PluginContext(archive.getParent(), working));
                response.put("success", true);
                response.put("id", plugin.getPluginId());
                response.put("version", plugin.getVersion());
            }
        } catch (ReflectiveOperationException | java.io.IOException
                | com.ssmt.core.exception.PluginLoadException
                | IllegalArgumentException exception) {
            response.put("success", false);
            response.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            exitCode = 1;
        }
        try {
            protocol.print(MAPPER.writeValueAsString(response));
            protocol.flush();
        } catch (java.io.IOException exception) {
            exitCode = 2;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
