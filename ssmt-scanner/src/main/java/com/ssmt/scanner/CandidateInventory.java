package com.ssmt.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Read-only, deterministic byte inventory; never asserts runtime compatibility. */
public final class CandidateInventory {
    private static final int MAX_FILES = 100_000;

    /** One regular file, named relative to the selected candidate. */
    public record Entry(String path, long bytes, String sha256, String kind) { }

    /** Captures every regular file beneath a directory, rejecting links and races. */
    public List<Entry> capture(Path candidate) throws IOException {
        Path requestedRoot = candidate.toAbsolutePath().normalize();
        rejectSymbolicComponents(requestedRoot);
        if (!Files.isDirectory(requestedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Candidate must be a directory: " + requestedRoot);
        }
        // Normalize equivalent Windows spellings once; linked descendants remain forbidden.
        Path root = requestedRoot.toRealPath();
        rejectLinks(root);
        List<Entry> entries = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                rejectLinks(path);
                BasicFileAttributes before = Files.readAttributes(path,
                        BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (before.isDirectory()) { continue; }
                if (!before.isRegularFile()) {
                    throw new IOException("Non-regular candidate entry: " + path);
                }
                if (entries.size() >= MAX_FILES) {
                    throw new IOException("Candidate file inventory limit exceeded");
                }
                String hash = hash(path);
                BasicFileAttributes after = Files.readAttributes(path,
                        BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (before.size() != after.size()
                        || !before.lastModifiedTime().equals(after.lastModifiedTime())
                        || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
                    throw new IOException("Candidate changed during inventory: " + path);
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                entries.add(new Entry(relative, before.size(), hash, kind(relative)));
            }
        }
        entries.sort(Comparator.comparing(Entry::path));
        return List.copyOf(entries);
    }

    private static void rejectLinks(Path path) throws IOException {
        rejectSymbolicComponents(path);
        if (!path.toRealPath().equals(path.toAbsolutePath().normalize())) {
            throw new IOException("Aliased candidate paths require explicit review: " + path);
        }
    }

    private static void rejectSymbolicComponents(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Linked candidate paths are not supported: " + current);
            }
        }
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String kind(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".java")) { return "JAVA_SOURCE"; }
        if (lower.endsWith(".class")) { return "LOOSE_CLASS"; }
        if (lower.endsWith(".jar")) { return "JAR"; }
        if (lower.endsWith(".csv")) { return "CSV"; }
        if (lower.endsWith(".json") || lower.endsWith(".ship")
                || lower.endsWith(".variant") || lower.endsWith(".wpn")
                || lower.endsWith(".faction")) { return "JSON_LIKE"; }
        if (lower.endsWith(".txt")) { return "TEXT"; }
        return "OTHER";
    }
}
