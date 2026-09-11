package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
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
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(file.toFile());
            if (root.path("schemaVersion").asInt(-1) != 1) {
                return Optional.empty();
            }
            String stored = root.path("modsDestination").asText("");
            if (stored.isBlank()) {
                return Optional.empty();
            }
            Path candidate = Path.of(stored).toAbsolutePath().normalize();
            return usableDirectory(candidate) ? Optional.of(candidate.toRealPath()) : Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Atomically remembers a validated destination after a successful publication. */
    public void rememberModsDestination(Path destination) throws ProjectException {
        Path normalized = destination.toAbsolutePath().normalize();
        if (!usableDirectory(normalized)) {
            throw new ProjectException("The selected mods destination is not a writable directory");
        }
        Path staging = file.resolveSibling(file.getFileName() + ".ssmt-stage");
        try {
            Path parent = file.getParent();
            if (parent == null) {
                throw new IOException("Settings file has no parent");
            }
            Files.createDirectories(parent);
            ObjectNode root = JSON.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("modsDestination", normalized.toRealPath().toString());
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
            throw new ProjectException("The translated copy was built, but its destination could not be remembered", exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // A failed staging cleanup must not hide the save result.
            }
        }
    }

    private static boolean usableDirectory(Path candidate) {
        return Files.isDirectory(candidate) && !Files.isSymbolicLink(candidate)
                && Files.isReadable(candidate) && Files.isWritable(candidate);
    }
}
