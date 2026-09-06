package com.ssmt.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Optional author-reviewed bytecode locations, bound to their exact source text. */
public final class BytecodeTextAllowlist {
    public static final String FILE_NAME = "ssmt-bytecode-allowlist.tsv";
    private final boolean configured;
    private final Map<String, String> hashes;

    private BytecodeTextAllowlist(boolean configured, Map<String, String> hashes) {
        this.configured = configured;
        this.hashes = Map.copyOf(hashes);
    }

    /** Reads a versioned, UTF-8 tab-separated catalog from the mod root. */
    public static BytecodeTextAllowlist read(Path modRoot) throws IOException {
        Path file = modRoot.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return new BytecodeTextAllowlist(false, Map.of());
        }
        if (Files.size(file) > 4 * 1024 * 1024) {
            throw new IOException("Bytecode allowlist exceeds 4 MiB");
        }
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.getFirst().equals("# ssmt-bytecode-allowlist-v1")) {
            throw new IOException("Unsupported bytecode allowlist version");
        }
        Map<String, String> hashes = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length != 3 || fields[0].isBlank() || fields[0].contains("\\")
                    || fields[0].startsWith("/") || fields[0].contains(":")
                    || java.util.Arrays.asList(fields[0].split("/", -1)).stream()
                            .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))
                    || !fields[1].startsWith("class:") || !fields[2].matches("[0-9a-f]{64}")) {
                throw new IOException("Malformed bytecode allowlist row");
            }
            if (hashes.put(fields[0] + "\t" + fields[1], fields[2]) != null) {
                throw new IOException("Duplicate bytecode allowlist location");
            }
        }
        return new BytecodeTextAllowlist(true, hashes);
    }

    /** Unlisted locations are excluded; a listed location with changed text fails closed. */
    public boolean permits(Path relativeFile, String key, String text) throws IOException {
        if (!configured) {
            return true;
        }
        String expected = hashes.get(relativeFile.toString().replace('\\', '/') + "\t" + key);
        if (expected == null) {
            return false;
        }
        if (!expected.equals(sha256(text))) {
            throw new IOException("Stale bytecode allowlist text at " + key);
        }
        return true;
    }

    /** Returns the digest used by the catalog without normalizing source text. */
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Keeps translated clones readable by rebinding approved locations to emitted text. */
    public String translatedCatalog(Map<String, String> translationsByFileAndKey) {
        StringBuilder result = new StringBuilder("# ssmt-bytecode-allowlist-v1\n");
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String translated = translationsByFileAndKey.get(entry.getKey());
            result.append(entry.getKey()).append('\t')
                    .append(translated == null ? entry.getValue() : sha256(translated)).append('\n');
        });
        return result.toString();
    }
}
