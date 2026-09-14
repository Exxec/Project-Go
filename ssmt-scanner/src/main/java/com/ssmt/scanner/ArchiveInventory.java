package com.ssmt.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Bounded ZIP byte inventory without extraction or candidate mutation. */
public final class ArchiveInventory {
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_BYTES = 1024L * 1024 * 1024;

    /** A regular archive entry; names retain their safe archive-relative spelling. */
    public record Entry(String path, long bytes, String sha256) { }

    /** Captures safe regular entries, counting actual decompressed bytes. */
    public List<Entry> capture(Path archive) throws IOException {
        Path file = archive.toAbsolutePath().normalize();
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Linked archives require review: " + current);
            }
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().equals(file)) {
            throw new IOException("Archive must be a regular canonical file: " + file);
        }
        var before = Files.readAttributes(file, BasicFileAttributes.class);
        List<Entry> result;
        try (var input = Files.newInputStream(file)) {
            result = capture(input);
        }
        var after = Files.readAttributes(file, BasicFileAttributes.class);
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Archive changed during inventory");
        }
        return result;
    }

    /**
     * Captures entries from an already-contained archive stream without writing it
     * to disk. The caller remains responsible for binding the stream bytes to an
     * outer inventory hash.
     */
    public List<Entry> capture(java.io.InputStream archive) throws IOException {
        List<Entry> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long total = 0;
        int count = 0;
        try (var zip = new ZipInputStream(archive)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > MAX_ENTRIES) {
                    throw new IOException("Archive entry limit exceeded");
                }
                String name = entry.getName();
                validateName(name, entry.isDirectory());
                String key = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
                if (!names.add(key.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Duplicate or case-colliding archive entry: " + name);
                }
                if (entry.isDirectory()) { continue; }
                MessageDigest digest = sha256();
                long bytes = 0;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    bytes += read;
                    if (total > MAX_BYTES) {
                        throw new IOException("Archive expansion limit exceeded");
                    }
                    digest.update(buffer, 0, read);
                }
                if (entry.getSize() >= 0 && entry.getSize() != bytes) {
                    throw new IOException("Archive entry size mismatch: " + name);
                }
                result.add(new Entry(name, bytes, HexFormat.of().formatHex(digest.digest())));
            }
        }
        result.sort(Comparator.comparing(Entry::path));
        return List.copyOf(result);
    }

    private static void validateName(String name, boolean directory) throws IOException {
        String path = directory && name.endsWith("/")
                ? name.substring(0, name.length() - 1) : name;
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")
                || path.contains(":")) {
            throw new IOException("Unsafe archive path: " + name);
        }
        for (String component : path.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")
                    || component.endsWith(".") || component.endsWith(" ")
                    || component.chars().anyMatch(value -> value < 32)) {
                throw new IOException("Unsafe archive path: " + name);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
