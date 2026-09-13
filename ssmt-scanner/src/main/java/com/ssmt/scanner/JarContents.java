package com.ssmt.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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

    private static String category(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".class")) { return "CLASS_ENTRY_UNVERIFIED"; }
        if (lower.endsWith(".java")) { return "BUNDLED_SOURCE"; }
        return "RESOURCE";
    }
}
