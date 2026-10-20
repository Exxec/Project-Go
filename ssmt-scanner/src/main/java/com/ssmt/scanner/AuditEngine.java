package com.ssmt.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only manifest comparison; stability alone is not runtime evidence. */
public final class AuditEngine {
    public record Entry(String name, long bytes, String sha256) { }
    public record Manifest(String candidate, long totalBytes, int entryCount, String hash) { }
    public record Ledger(String candidate, long before, long after,
                         int entries, String drift, String verdict) { }

    public List<Entry> capture(String candidate, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return new CandidateInventory().capture(path).stream()
                    .map(e -> new Entry(e.path(), e.bytes(), e.sha256())).toList();
        }
        return new ArchiveInventory().capture(path).stream()
                .map(e -> new Entry(e.path(), e.bytes(), e.sha256())).toList();
    }

    public Ledger compare(String candidate, List<Entry> before, List<Entry> after) {
        Map<String, Entry> original = index(before);
        Map<String, Entry> current = index(after);
        boolean stable = original.equals(current);
        return new Ledger(candidate, before.stream().mapToLong(Entry::bytes).sum(),
                after.stream().mapToLong(Entry::bytes).sum(), after.size(),
                stable ? "STABLE" : "DRIFTED", stable ? "PASS" : "FAIL");
    }

    private static Map<String, Entry> index(List<Entry> entries) {
        Map<String, Entry> result = new HashMap<>();
        for (Entry entry : entries) {
            new InventoryFingerprint.File(entry.name(), entry.bytes(), entry.sha256());
            if (result.putIfAbsent(entry.name(), entry) != null) {
                throw new IllegalArgumentException("Duplicate inventory path: " + entry.name());
            }
        }
        return result;
    }
}
