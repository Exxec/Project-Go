package com.ssmt.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Applies optional host OS restrictions to a plugin worker command.
 */
final class SandboxCommandFactory {
    private final String operatingSystem;
    private final ExecutableLocator executables;

    SandboxCommandFactory() {
        this(System.getProperty("os.name"), SandboxCommandFactory::findExecutable);
    }

    SandboxCommandFactory(String operatingSystem, ExecutableLocator executables) {
        if (operatingSystem == null || executables == null) {
            throw new IllegalArgumentException("Sandbox environment must not be null");
        }
        this.operatingSystem = operatingSystem.toLowerCase(Locale.ROOT);
        this.executables = executables;
    }

    List<String> wrap(
            List<String> worker,
            Path workingDirectory,
            PluginSandboxProfile profile) throws PluginActivationException {
        List<String> command = List.copyOf(worker);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (profile == PluginSandboxProfile.PROCESS_ONLY) {
            return command;
        }
        Optional<List<String>> wrapped;
        if (operatingSystem.contains("linux")) {
            wrapped = executables.find("bwrap")
                    .map(executable -> bubblewrap(executable, workingDirectory, command));
        } else if (operatingSystem.contains("mac")) {
            wrapped = executables.find("sandbox-exec")
                    .map(executable -> macSandbox(executable, workingDirectory, command));
        } else {
            wrapped = Optional.empty();
        }
        if (wrapped.isPresent()) {
            return wrapped.orElseThrow();
        }
        if (profile == PluginSandboxProfile.REQUIRED) {
            throw new PluginActivationException(
                    "Required OS plugin sandbox is unavailable on " + operatingSystem);
        }
        return command;
    }

    private static List<String> bubblewrap(
            Path executable,
            Path working,
            List<String> worker) {
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--die-with-parent",
                "--unshare-all",
                "--new-session",
                "--ro-bind", "/", "/",
                "--bind", working.toString(), working.toString(),
                "--chdir", working.toString(),
                "--"));
        command.addAll(worker);
        return List.copyOf(command);
    }

    private static List<String> macSandbox(
            Path executable,
            Path working,
            List<String> worker) {
        String escaped = working.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        String policy = "(version 1)"
                + "(deny default)"
                + "(allow process*)"
                + "(allow file-read*)"
                + "(allow file-write* (subpath \"" + escaped + "\"))"
                + "(deny network*)";
        List<String> command = new ArrayList<>(List.of(
                executable.toString(), "-p", policy));
        command.addAll(worker);
        return List.copyOf(command);
    }

    private static Optional<Path> findExecutable(String name) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return Optional.empty();
        }
        for (String directory : pathValue.split(Pattern.quote(java.io.File.pathSeparator))) {
            if (!directory.isBlank()) {
                Path candidate = Path.of(directory).resolve(name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath().normalize());
                }
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    interface ExecutableLocator {
        Optional<Path> find(String name);
    }
}
