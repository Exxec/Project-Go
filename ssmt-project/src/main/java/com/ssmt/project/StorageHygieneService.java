package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Read-first, hash-bound cleanup for application-owned cache and staging data. */
public final class StorageHygieneService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_VISITED_PATHS = 20_000;
    private final Path root;

    /** Kinds of disposable application-owned storage. */
    public enum Kind { ARCHIVE_CACHE, INTERRUPTED_STAGING }

    /** One exact cleanup candidate. */
    public record Item(String relativePath, Kind kind, long bytes, int files, String treeHash) { }

    /** Immutable cleanup preview that must be supplied unchanged to cleanup. */
    public record Preview(int schemaVersion, Path root, List<Item> items, long bytes, int files) {
        public Preview {
            items = List.copyOf(items);
        }
    }

    /** Uses the normal application-data root. */
    public StorageHygieneService() {
        this(TranslationWorkflow.defaultApplicationRoot());
    }

    /** Uses an explicit application-owned root. */
    public StorageHygieneService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Lists old, known-disposable paths without changing them. */
    public Preview preview(Duration minimumAge) throws ProjectException {
        Objects.requireNonNull(minimumAge, "minimumAge");
        if (minimumAge.isNegative()) {
            throw new ProjectException("Cleanup age must not be negative");
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return new Preview(1, root, List.of(), 0, 0);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new ProjectException("Application storage root must be a real directory");
        }
        Instant cutoff = Instant.now().minus(minimumAge);
        var items = new ArrayList<Item>();
        try (var paths = Files.walk(root, 5)) {
            List<Path> inspected = new ArrayList<>(
                    paths.limit(MAX_VISITED_PATHS + 1L).toList());
            if (inspected.size() > MAX_VISITED_PATHS) {
                throw new ProjectException("Application storage contains too many paths to inspect safely");
            }
            inspected.sort(Comparator.naturalOrder());
            for (Path candidate : inspected) {
                if (candidate.equals(root) || Files.isSymbolicLink(candidate)
                        || Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(cutoff)) {
                    continue;
                }
                Kind kind = classify(candidate);
                if (kind == null || nestedBelowExisting(items, candidate)
                        || !entireTreeOlderThan(candidate, cutoff)) {
                    continue;
                }
                items.add(inventory(candidate, kind));
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not inspect application storage", exception);
        }
        items.sort(Comparator.comparing(Item::relativePath));
        return new Preview(1, root, items, items.stream().mapToLong(Item::bytes).sum(),
                items.stream().mapToInt(Item::files).sum());
    }

    /** Saves the exact preview as a portable approval manifest. */
    public void writePreview(Path destination, Preview preview) throws ProjectException {
        if (!root.equals(preview.root().toAbsolutePath().normalize())) {
            throw new ProjectException("Cleanup preview does not belong to this application storage root");
        }
        ObjectNode document = JSON.createObjectNode();
        document.put("schemaVersion", preview.schemaVersion());
        document.put("root", preview.root().toString());
        document.put("bytes", preview.bytes());
        document.put("files", preview.files());
        var items = document.putArray("items");
        for (Item item : preview.items()) {
            ObjectNode value = items.addObject();
            value.put("relativePath", item.relativePath());
            value.put("kind", item.kind().name());
            value.put("bytes", item.bytes());
            value.put("files", item.files());
            value.put("treeHash", item.treeHash());
        }
        Path target = destination.toAbsolutePath().normalize();
        Path staging = target.resolveSibling(target.getFileName() + ".ssmt-stage");
        try {
            Files.createDirectories(Objects.requireNonNull(target.getParent(), "manifest parent"));
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), document);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            throw new ProjectException("Could not save cleanup preview", exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // Failed cleanup does not change the manifest publication result.
            }
        }
    }

    /** Reads a previously reviewed cleanup manifest without discovering new candidates. */
    public Preview readPreview(Path source) throws ProjectException {
        try {
            var document = JSON.readTree(source.toFile());
            var items = new ArrayList<Item>();
            for (var value : document.withArray("items")) {
                items.add(new Item(value.path("relativePath").asText(),
                        Kind.valueOf(value.path("kind").asText()), value.path("bytes").asLong(-1),
                        value.path("files").asInt(-1), value.path("treeHash").asText()));
            }
            Preview preview = new Preview(document.path("schemaVersion").asInt(-1),
                    Path.of(document.path("root").asText()), items,
                    document.path("bytes").asLong(-1), document.path("files").asInt(-1));
            long bytes = items.stream().mapToLong(Item::bytes).sum();
            int files = items.stream().mapToInt(Item::files).sum();
            List<Item> sorted = items.stream().sorted(Comparator.comparing(Item::relativePath)).toList();
            if (preview.schemaVersion() != 1
                    || !root.equals(preview.root().toAbsolutePath().normalize())
                    || preview.bytes() != bytes || preview.files() != files || !items.equals(sorted)) {
                throw new ProjectException("Cleanup preview manifest is inconsistent");
            }
            return preview;
        } catch (IOException | RuntimeException exception) {
            throw new ProjectException("Could not read cleanup preview", exception);
        }
    }

    /** Deletes only the exact, unchanged candidates recorded by a prior preview. */
    public void cleanup(Preview approved) throws ProjectException {
        if (approved.schemaVersion() != 1 || !root.equals(approved.root().toAbsolutePath().normalize())) {
            throw new ProjectException("Cleanup preview does not belong to this application storage root");
        }
        for (Item item : approved.items()) {
            Path target = resolveContained(item.relativePath());
            if (classify(target) != item.kind()) {
                throw new ProjectException("Cleanup preview contains a protected path: " + item.relativePath());
            }
            Item current = inventory(target, item.kind());
            if (!current.equals(item)) {
                throw new ProjectException("Cleanup candidate changed after preview: " + item.relativePath());
            }
        }
        try {
            for (Item item : approved.items()) {
                Path target = resolveContained(item.relativePath());
                try (var paths = Files.walk(target)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not clean approved application storage", exception);
        }
    }

    private Kind classify(Path path) {
        String name = Objects.requireNonNull(path.getFileName()).toString();
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && name.startsWith("archive-source-")) {
            return Kind.ARCHIVE_CACHE;
        }
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && (name.startsWith(".candidate-") || name.endsWith(".ssmt-stage"))) {
            return Kind.INTERRUPTED_STAGING;
        }
        return null;
    }

    private boolean nestedBelowExisting(List<Item> items, Path candidate) {
        String relative = relative(candidate);
        return items.stream().anyMatch(item -> relative.startsWith(item.relativePath() + "/"));
    }

    private static boolean entireTreeOlderThan(Path candidate, Instant cutoff)
            throws IOException, ProjectException {
        try (var paths = Files.walk(candidate)) {
            List<Path> entries = paths.limit(MAX_VISITED_PATHS + 1L).toList();
            if (entries.size() > MAX_VISITED_PATHS) {
                throw new ProjectException("Cleanup candidate contains too many paths");
            }
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry)
                        || Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS)
                                .toInstant().isAfter(cutoff)) {
                    return false;
                }
            }
            return true;
        }
    }

    private Item inventory(Path target, Kind kind) throws ProjectException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new ProjectException("Cleanup candidate disappeared or became unsafe: " + relative(target));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            int files = 0;
            try (var paths = Files.walk(target)) {
                List<Path> entries = paths.sorted(Comparator.comparing(this::relative)).toList();
                if (entries.size() > MAX_VISITED_PATHS) {
                    throw new ProjectException("Cleanup candidate contains too many paths: " + relative(target));
                }
                byte[] buffer = new byte[8192];
                for (Path path : entries) {
                    if (Files.isSymbolicLink(path)) {
                        throw new ProjectException("Cleanup candidate contains a symbolic link: " + relative(path));
                    }
                    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                    String entry = target.relativize(path).toString().replace('\\', '/');
                    digest.update(entry.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(attributes.isDirectory() ? (byte) 'D' : (byte) 'F');
                    if (attributes.isRegularFile()) {
                        files++;
                        bytes = Math.addExact(bytes, attributes.size());
                        try (InputStream input = Files.newInputStream(path)) {
                            int read;
                            while ((read = input.read(buffer)) >= 0) {
                                digest.update(buffer, 0, read);
                            }
                        }
                    }
                }
            }
            return new Item(relative(target), kind, bytes, files,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException | ArithmeticException exception) {
            throw new ProjectException("Could not inventory cleanup candidate: " + relative(target), exception);
        }
    }

    private Path resolveContained(String relative) throws ProjectException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new ProjectException("Cleanup candidate escapes application storage");
        }
        return target;
    }

    private String relative(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
