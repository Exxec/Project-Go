package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Small, application-owned preferences for the normal file workflow. */
public final class WorkflowPreferences {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path file;

    /** Uses the normal application-data settings file. */
    public WorkflowPreferences() {
        this(TranslationWorkflow.defaultApplicationRoot().resolve("settings.json"));
    }

    /** Uses an explicit settings file, primarily for isolated front ends and tests. */
    public WorkflowPreferences(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    /** Returns the remembered destination only while it remains a usable directory. */
    public Optional<Path> modsDestination() {
        try {
            JsonNode root = readSettings().orElse(null);
            if (root == null) { return Optional.empty(); }
            String stored = root.path("modsDestination").asText("");
            if (stored.isBlank()) {
                return Optional.empty();
            }
            Path candidate = Path.of(stored).toAbsolutePath().normalize();
            return usableDirectory(candidate) ? Optional.of(candidate.toRealPath()) : Optional.empty();
        } catch (IOException | ProjectException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Returns a validated output whose publication or recovery may be incomplete. */
    public Optional<Path> attemptedOutput() {
        try {
            JsonNode root = readSettings().orElse(null);
            if (root == null) { return Optional.empty(); }
            String stored = root.path("attemptedOutput").asText("");
            if (stored.isBlank()) { return Optional.empty(); }
            Path candidate = Path.of(stored);
            if (!candidate.isAbsolute()) { return Optional.empty(); }
            return Optional.of(normalizeOutput(candidate));
        } catch (ProjectException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Durably records the exact output before publication begins. */
    public void rememberAttemptedOutput(Path output) throws ProjectException {
        Path normalized = normalizeOutput(output);
        ObjectNode root = settingsForUpdate();
        root.put("attemptedOutput", normalized.toString());
        writeSettings(root, "Could not remember the attempted translated-copy output");
    }

    /** Atomically remembers a validated destination after a successful publication. */
    public void rememberModsDestination(Path destination) throws ProjectException {
        Path normalized = normalizeDestination(destination);
        ObjectNode root = settingsForUpdate();
        root.put("modsDestination", normalized.toString());
        writeSettings(root,
                "The translated copy was built, but its destination could not be remembered");
    }

    /**
     * Atomically records a successful destination and clears its matching attempted output.
     */
    public void rememberSuccessfulPublication(Path destination, Path publishedOutput)
            throws ProjectException {
        Path normalizedDestination = normalizeDestination(destination);
        Path normalizedOutput = normalizeOutput(publishedOutput);
        ObjectNode root = settingsForUpdate();
        requireMatchingAttempt(root, normalizedOutput);
        root.put("modsDestination", normalizedDestination.toString());
        root.remove("attemptedOutput");
        writeSettings(root,
                "The translated copy was built, but its successful output could not be remembered");
    }

    /** Clears a matching attempted output after successful publication or recovery. */
    public void clearAttemptedOutput(Path completedOutput) throws ProjectException {
        Path normalized = normalizeOutput(completedOutput);
        ObjectNode root = settingsForUpdate();
        requireMatchingAttempt(root, normalized);
        if (root.remove("attemptedOutput") != null) {
            writeSettings(root, "Could not clear the completed translated-copy output");
        }
    }

    private Optional<ObjectNode> readSettings() throws ProjectException {
        if (!Files.isRegularFile(file)) { return Optional.empty(); }
        try {
            JsonNode root = JSON.readTree(file.toFile());
            if (!(root instanceof ObjectNode object)
                    || root.path("schemaVersion").asInt(-1) != 1) {
                return Optional.empty();
            }
            return Optional.of(object);
        } catch (IOException exception) {
            throw new ProjectException("Could not read workflow settings", exception);
        }
    }

    private ObjectNode settingsForUpdate() throws ProjectException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            ObjectNode created = JSON.createObjectNode();
            created.put("schemaVersion", 1);
            return created;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)) {
            throw new ProjectException("Workflow settings must be a regular file");
        }
        try {
            JsonNode root = JSON.readTree(file.toFile());
            if (!(root instanceof ObjectNode object)
                    || root.path("schemaVersion").asInt(-1) != 1) {
                throw new ProjectException("Unsupported or invalid workflow settings");
            }
            return object.deepCopy();
        } catch (IOException exception) {
            throw new ProjectException("Could not read workflow settings", exception);
        }
    }

    private void writeSettings(ObjectNode root, String errorMessage) throws ProjectException {
        Path staging = file.resolveSibling(file.getFileName() + ".ssmt-stage");
        try {
            Path parent = file.getParent();
            if (parent == null) {
                throw new IOException("Settings file has no parent");
            }
            Files.createDirectories(parent);
            root.put("schemaVersion", 1);
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), root);
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProjectException(errorMessage, exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // A failed staging cleanup must not hide the save result.
            }
        }
    }

    private static void requireMatchingAttempt(ObjectNode root, Path completed)
            throws ProjectException {
        String stored = root.path("attemptedOutput").asText("");
        if (!stored.isBlank()) {
            Path attempted;
            try {
                attempted = normalizeOutput(Path.of(stored));
            } catch (RuntimeException exception) {
                throw new ProjectException("Saved attempted output is invalid", exception);
            }
            if (!attempted.equals(completed)) {
                throw new ProjectException(
                        "Completed output does not match the saved attempted output");
            }
        }
    }

    private static Path normalizeDestination(Path destination) throws ProjectException {
        Path normalized = destination.toAbsolutePath().normalize();
        if (!usableDirectory(normalized)) {
            throw new ProjectException("The selected mods destination is not a writable directory");
        }
        try {
            return normalized.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException("Could not resolve the selected mods destination", exception);
        }
    }

    private static Path normalizeOutput(Path output) throws ProjectException {
        Path normalized = output.toAbsolutePath().normalize();
        if (normalized.getFileName() == null) {
            throw new ProjectException("The translated-copy output must not be a filesystem root");
        }
        Path existing = normalized;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null || Files.isSymbolicLink(existing)
                || !Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(existing) || !Files.isWritable(existing)) {
            throw new ProjectException(
                    "The translated-copy output must have a writable real-directory ancestor");
        }
        try {
            Path realAncestor = existing.toRealPath();
            if (!realAncestor.equals(existing.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw new ProjectException(
                        "The translated-copy output must not pass through a symbolic link");
            }
            return realAncestor.resolve(existing.relativize(normalized)).normalize();
        } catch (IOException exception) {
            throw new ProjectException("Could not resolve the translated-copy output", exception);
        }
    }

    private static boolean usableDirectory(Path candidate) {
        return Files.isDirectory(candidate) && !Files.isSymbolicLink(candidate)
                && Files.isReadable(candidate) && Files.isWritable(candidate);
    }
}
