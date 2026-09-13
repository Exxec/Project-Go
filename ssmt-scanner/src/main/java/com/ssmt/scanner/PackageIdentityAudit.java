package com.ssmt.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Independent directory/ZIP byte comparison; no packaging or mutation occurs. */
public final class PackageIdentityAudit {
    /** Deterministic differences relative to the selected directory root. */
    public record Result(List<String> missing, List<String> extra, List<String> changed) {
        public Result {
            missing = List.copyOf(missing);
            extra = List.copyOf(extra);
            changed = List.copyOf(changed);
        }

        /** Identity only; not source authority, runtime or distribution permission. */
        public boolean identical() {
            return missing.isEmpty() && extra.isEmpty() && changed.isEmpty();
        }
    }

    /**
     * Compares all files. Wrapper selection is explicit, never inferred to hide extras.
     * Files outside an explicitly selected wrapper remain reported as extra.
     */
    public Result compare(Path directory, Path archive, String archiveRoot) throws IOException {
        String root = archiveRoot == null ? "" : archiveRoot;
        if (!root.isEmpty() && (root.startsWith("/") || root.endsWith("/")
                || root.contains("\\") || root.contains(":"))) {
            throw new IOException("Archive root must be a safe relative path without trailing slash");
        }
        for (String component : root.split("/", -1)) {
            if (!root.isEmpty() && (component.isEmpty() || component.equals(".")
                    || component.equals(".."))) {
                throw new IOException("Unsafe archive root");
            }
        }
        var expected = new TreeMap<String, CandidateInventory.Entry>();
        for (var entry : new CandidateInventory().capture(directory)) {
            expected.put(entry.path(), entry);
        }
        var actual = new TreeMap<String, ArchiveInventory.Entry>();
        List<String> extra = new ArrayList<>();
        String prefix = root.isEmpty() ? "" : root + "/";
        for (var entry : new ArchiveInventory().capture(archive)) {
            if (!entry.path().startsWith(prefix)) {
                extra.add(entry.path());
            } else {
                actual.put(entry.path().substring(prefix.length()), entry);
            }
        }
        List<String> missing = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (var entry : expected.entrySet()) {
            var packaged = actual.remove(entry.getKey());
            if (packaged == null) {
                missing.add(entry.getKey());
            } else if (packaged.bytes() != entry.getValue().bytes()
                    || !packaged.sha256().equals(entry.getValue().sha256())) {
                changed.add(entry.getKey());
            }
        }
        extra.addAll(actual.keySet());
        extra.sort(String::compareTo);
        return new Result(missing, extra, changed);
    }
}
