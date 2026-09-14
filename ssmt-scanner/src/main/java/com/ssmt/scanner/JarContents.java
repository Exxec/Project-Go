package com.ssmt.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.zip.ZipFile;

/** JAR payload inventory; entry suffixes do not prove bytecode/source equivalence. */
public record JarContents(String path, String containerSha256, List<Entry> entries,
        String sourceJarCorrespondence) {
    public JarContents { entries = List.copyOf(entries); }

    /** Payload bytes and their category, without class definition or execution. */
    public record Entry(String path, long bytes, String sha256, String category) { }

    /** Reads an explicitly inventoried mod-relative JAR, bound to its expected hash. */
    public static JarContents inspect(Path modRoot, Path relative, String expectedHash) throws IOException {
        Path root = modRoot.toAbsolutePath().normalize();
        Path jar = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !jar.startsWith(root)) {
            throw new IOException("JAR path must remain beneath the candidate root");
        }
        var payloads = new ArchiveInventory().capture(jar);
        String actualHash = InventoryFingerprint.archive(jar);
        if (!actualHash.equals(expectedHash)) {
            throw new IOException("JAR bytes changed after candidate inventory");
        }
        List<Entry> entries = payloads.stream().map(entry -> new Entry(entry.path(), entry.bytes(),
                entry.sha256(), category(entry.path()))).toList();
        return new JarContents(relative.toString().replace('\\', '/'), actualHash, entries, "NOT_ESTABLISHED");
    }

    /**
     * Inspects a JAR stored inside an outer ZIP without extracting either archive.
     * The outer entry is independently inventoried first and the nested stream is
     * re-hashed while its payload ZIP is read.
     */
    public static JarContents inspectArchiveEntry(Path archive, Path relative, String expectedHash)
            throws IOException {
        String path = relative.toString().replace('\\', '/');
        var outer = new ArchiveInventory().capture(archive);
        var selected = outer.stream().filter(entry -> entry.path().equals(path)).findFirst()
                .orElseThrow(() -> new IOException("Embedded JAR is absent from archive inventory: " + path));
        if (!selected.sha256().equals(expectedHash)) {
            throw new IOException("Embedded JAR changed after candidate inventory");
        }
        List<ArchiveInventory.Entry> payloads;
        String actualHash;
        try (var zip = new ZipFile(archive.toFile())) {
            var entry = zip.getEntry(path);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Embedded JAR disappeared from archive: " + path);
            }
            try (var input = new DigestInputStream(zip.getInputStream(entry), sha256())) {
                payloads = new ArchiveInventory().capture(input);
                actualHash = java.util.HexFormat.of().formatHex(input.getMessageDigest().digest());
            }
        }
        if (!actualHash.equals(expectedHash)) {
            throw new IOException("Embedded JAR bytes changed during payload inventory");
        }
        List<Entry> entries = payloads.stream().map(entry -> new Entry(entry.path(), entry.bytes(),
                entry.sha256(), category(entry.path()))).toList();
        return new JarContents(path, actualHash, entries, "NOT_ESTABLISHED");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String category(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".class")) { return "CLASS_ENTRY_UNVERIFIED"; }
        if (lower.endsWith(".java")) { return "BUNDLED_SOURCE"; }
        return "RESOURCE";
    }
}
