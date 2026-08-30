package com.ssmt.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Initializes a cataloged plugin in a bounded worker JVM.
 */
public final class PluginActivator {
    private static final int MAX_CAPTURE_BYTES = 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SandboxCommandFactory sandboxes = new SandboxCommandFactory();

    /**
     * Runs plugin initialization in a short-lived child JVM.
     *
     * @param descriptor validated catalog descriptor
     * @param workingDirectory isolated writable directory
     * @param timeout maximum worker runtime
     * @return verified runtime identity
     * @throws PluginActivationException on worker, timeout, or identity failure
     */
    public PluginActivationResult activate(
            PluginDescriptor descriptor,
            Path workingDirectory,
            Duration timeout) throws PluginActivationException {
        return activate(
                descriptor,
                workingDirectory,
                timeout,
                PluginSandboxProfile.PROCESS_ONLY);
    }

    /**
     * Runs plugin initialization with an explicit OS sandbox policy.
     *
     * @param descriptor validated catalog descriptor
     * @param workingDirectory isolated writable directory
     * @param timeout maximum worker runtime
     * @param sandboxProfile requested sandbox strength
     * @return verified runtime identity
     * @throws PluginActivationException on sandbox, worker, timeout, or identity failure
     */
    public PluginActivationResult activate(
            PluginDescriptor descriptor,
            Path workingDirectory,
            Duration timeout,
            PluginSandboxProfile sandboxProfile) throws PluginActivationException {
        if (descriptor == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid plugin activation request");
        }
        Path working = workingDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(working)) {
            throw new PluginActivationException("Plugin working directory does not exist: " + working);
        }
        List<String> command = sandboxes.wrap(
                command(descriptor, working),
                working,
                sandboxProfile);
        ProcessBuilder builder = new ProcessBuilder(command).directory(working.toFile());
        sanitizeEnvironment(builder.environment());
        try {
            Process process = builder.start();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<String> output = executor.submit(
                        () -> capture(process.getInputStream()));
                Future<String> error = executor.submit(
                        () -> capture(process.getErrorStream()));
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new PluginActivationException(
                            "Plugin activation timed out: " + descriptor.id());
                }
                String standardOutput = result(output);
                String standardError = result(error);
                if (process.exitValue() != 0) {
                    throw new PluginActivationException(
                            "Plugin worker failed: " + standardError.strip());
                }
                return parseResult(descriptor, standardOutput);
            }
        } catch (IOException exception) {
            throw new PluginActivationException("Could not launch plugin worker", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PluginActivationException("Plugin activation was interrupted", exception);
        }
    }

    private static List<String> command(PluginDescriptor descriptor, Path working) {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String executableName =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe" : "java";
        Path java = javaHome.resolve("bin").resolve(executableName);
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Duser.home=" + working);
        command.add("-Djava.io.tmpdir=" + working);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(PluginWorkerMain.class.getName());
        command.add(descriptor.archive().toString());
        command.add(descriptor.providerClass());
        command.add(descriptor.id());
        command.add(descriptor.version());
        command.add(working.toString());
        return List.copyOf(command);
    }

    private static void sanitizeEnvironment(Map<String, String> environment) {
        String systemRoot = valueIgnoreCase(environment, "SystemRoot");
        String windir = valueIgnoreCase(environment, "WINDIR");
        environment.clear();
        if (systemRoot != null) {
            environment.put("SystemRoot", systemRoot);
        }
        if (windir != null) {
            environment.put("WINDIR", windir);
        }
    }

    private static String valueIgnoreCase(Map<String, String> values, String key) {
        return values.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String capture(java.io.InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_CAPTURE_BYTES + 1);
        if (bytes.length > MAX_CAPTURE_BYTES) {
            throw new IOException("Plugin worker output exceeded capture limit");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String result(Future<String> future)
            throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            throw new IOException("Could not capture plugin worker output", exception.getCause());
        }
    }

    private static PluginActivationResult parseResult(
            PluginDescriptor descriptor,
            String output) throws PluginActivationException {
        try {
            JsonNode response = MAPPER.readTree(output);
            if (response == null || !response.path("success").asBoolean(false)) {
                throw new PluginActivationException(
                        "Plugin initialization failed: " + response.path("error").asText("unknown"));
            }
            PluginActivationResult result = new PluginActivationResult(
                    response.path("id").asText(),
                    response.path("version").asText());
            if (!descriptor.id().equals(result.id())
                    || !descriptor.version().equals(result.version())) {
                throw new PluginActivationException("Plugin runtime identity differs from manifest");
            }
            return result;
        } catch (IOException | IllegalArgumentException exception) {
            throw new PluginActivationException("Malformed plugin worker response", exception);
        }
    }
}
