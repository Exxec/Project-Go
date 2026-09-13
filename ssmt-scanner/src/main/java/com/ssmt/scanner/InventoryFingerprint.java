package com.ssmt.scanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Portable, versioned digest of relative file paths, sizes and content hashes. */
public final class InventoryFingerprint {
    private InventoryFingerprint() { }

    /** Input tuple independent of directory/ZIP wrapper and local absolute paths. */
    public record File(String path, long bytes, String sha256) {
        public File {
            if (path == null || path.isEmpty() || bytes < 0 || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid inventory fingerprint tuple");
            }
        }
    }

    /** Length-prefixes paths so embedded delimiters cannot create ambiguous digests. */
    public static String tree(List<File> files) {
        MessageDigest digest = digest();
        digest.update("ProjectGo-inventory-v1\0".getBytes(StandardCharsets.UTF_8));
        var names = new HashSet<String>();
        for (File file : files.stream().sorted(Comparator.comparing(File::path)).toList()) {
            if (!names.add(file.path())) {
                throw new IllegalArgumentException("Repeated fingerprint path: " + file.path());
            }
            byte[] path = file.path().getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(path.length).array());
            digest.update(path);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(file.bytes()).array());
            digest.update(HexFormat.of().parseHex(file.sha256()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Hashes exact container bytes, without decompressing or writing. */
    public static String archive(Path archive) throws IOException {
        Path file = archive.toAbsolutePath().normalize();
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Linked archive hash inputs require review");
            }
        }
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().equals(file)) {
            throw new IOException("Archive hash input must be a regular canonical file");
        }
        var before = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        MessageDigest digest = digest();
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
        }
        var after = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Archive changed while hashing exact bytes");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
