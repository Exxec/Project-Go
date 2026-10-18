package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pre-apply snapshot of the files an in-place apply will change, kept beside them under
 * {@code .ssmt-backup/<timestamp>/} with a manifest of SHA-256 hashes, plus the verified restore.
 */
public final class TranslationSnapshot {
    /** Folder name inside the mod; excluded from extraction and clone copies. */
    public static final String DIRECTORY = ".ssmt-backup";
    static final String MANIFEST = "manifest.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'", Locale.ROOT);

    private TranslationSnapshot() { }

    /** Copies every listed file (absolute paths inside root) into a new timestamped snapshot; returns the timestamp. */
    static String create(Path root, Collection<Path> files) throws IOException {
        Path base = root.resolve(DIRECTORY);
        Files.createDirectories(base);
        String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(STAMP);
        Path directory = base.resolve(stamp);
        for (int suffix = 1; Files.exists(directory); suffix++) {
            directory = base.resolve(stamp + "-" + suffix);
        }
        Files.createDirectory(directory);
        ObjectNode hashes = JSON.createObjectNode();
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            Path copy = directory.resolve(relative);
            Files.createDirectories(java.util.Objects.requireNonNull(copy.getParent()));
            Files.copy(file, copy, StandardCopyOption.COPY_ATTRIBUTES);
            hashes.put(relative, BridgeForgeTranslationService.hash(copy));
        }
        ObjectNode manifest = JSON.createObjectNode().put("schema_version", 1)
                .put("created", ZonedDateTime.now(ZoneOffset.UTC).toString());
        manifest.set("files", hashes);
        writeAtomic(directory.resolve(MANIFEST), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        return String.valueOf(directory.getFileName());
    }

    /** Removes a snapshot that was not needed (the apply was rolled back completely). */
    static void discard(Path root, String timestamp) throws IOException {
        Path directory = root.resolve(DIRECTORY).resolve(timestamp);
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                path.toFile().setWritable(true);
                Files.delete(path);
            }
        }
    }

    /** Lists snapshot timestamps, oldest first. */
    public static List<String> list(Path mod) throws IOException {
        Path base = mod.resolve(DIRECTORY);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (var children = Files.list(base)) {
            return children.filter(Files::isDirectory).map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    /**
     * Restores the files of one snapshot over the mod and verifies each restored file against the manifest.
     * The snapshot copies are verified first; nothing is written when any copy fails its recorded hash.
     *
     * @return relative paths restored
     */
    public static List<String> restore(Path mod, String timestamp) throws ProjectException {
        try {
            if (timestamp.isBlank() || timestamp.contains("/") || timestamp.contains("\\") || timestamp.contains("..")) {
                throw new IOException("Invalid snapshot name " + timestamp);
            }
            Path root = mod.toRealPath();
            Path directory = root.resolve(DIRECTORY).resolve(timestamp);
            if (!Files.isRegularFile(directory.resolve(MANIFEST))) {
                throw new IOException("No snapshot " + timestamp + " (known: " + list(root) + ")");
            }
            JsonNode files = JSON.readTree(Files.readAllBytes(directory.resolve(MANIFEST))).path("files");
            List<String> relatives = new ArrayList<>();
            files.fieldNames().forEachRemaining(relatives::add);
            for (String relative : relatives) {
                Path copy = BridgeForgeTranslationService.contained(directory, relative);
                if (!Files.isRegularFile(copy) || !BridgeForgeTranslationService.hash(copy)
                        .equalsIgnoreCase(files.path(relative).asText())) {
                    throw new IOException("Snapshot copy fails its recorded hash: " + relative);
                }
            }
            for (String relative : relatives) {
                Path target = BridgeForgeTranslationService.contained(root, relative);
                writeAtomic(target, Files.readAllBytes(BridgeForgeTranslationService.contained(directory, relative)));
            }
            for (String relative : relatives) {
                if (!BridgeForgeTranslationService.hash(BridgeForgeTranslationService.contained(root, relative))
                        .equalsIgnoreCase(files.path(relative).asText())) {
                    throw new IOException("Restored file fails its recorded hash: " + relative);
                }
            }
            return List.copyOf(relatives);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not restore snapshot: " + exception.getMessage(), exception);
        }
    }

    /** Writes through a sibling temp file and an atomic replace, so a crash never leaves a half-written target. */
    static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(java.util.Objects.requireNonNull(target.getParent()), ".ssmt-write-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (Files.exists(target)) {
                target.toFile().setWritable(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }
}
