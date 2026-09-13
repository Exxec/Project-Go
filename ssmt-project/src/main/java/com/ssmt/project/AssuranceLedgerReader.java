package com.ssmt.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Validates ledger structure and evidence bytes, not arbitrary evidence semantics. */
public final class AssuranceLedgerReader {
    private static final int MAX_LEDGER_BYTES = 1024 * 1024;
    private static final long MAX_EVIDENCE_BYTES = 64L * 1024 * 1024;

    /** Evidence paths are portable relative paths beneath the ledger's directory. */
    public record Reference(String path, String sha256) {
        public Reference {
            if (path == null || path.isBlank() || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid evidence reference");
            }
        }
    }

    /** Completion state is deliberately absent: it must be derived, not declared. */
    public record Ledger(int schemaVersion, String candidateSha256,
            List<AssuranceSummary.Result> results, List<Reference> references) {
        public Ledger {
            if (candidateSha256 == null || !candidateSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Ledger requires a candidate SHA-256");
            }
            results = List.copyOf(results);
            references = List.copyOf(references);
        }
    }

    /** Reads an exact-candidate ledger and hashes bounded, contained evidence references. */
    public AssuranceSummary.Summary read(Path ledgerFile, String expectedCandidateSha256) throws IOException {
        Path file = ledgerFile.toAbsolutePath().normalize();
        safeFile(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_LEDGER_BYTES + 1); }
        if (bytes.length > MAX_LEDGER_BYTES) { throw new IOException("Ledger exceeds 1 MiB"); }
        var mapper = JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        Ledger ledger = mapper.readValue(bytes, Ledger.class);
        if (ledger == null || ledger.schemaVersion() != 1) { throw new IOException("Unsupported ledger schema"); }
        if (!ledger.candidateSha256().equals(expectedCandidateSha256)) {
            throw new IOException("Ledger belongs to a different candidate");
        }
        AssuranceSummary.Summary summary;
        try { summary = new AssuranceSummary().summarize(expectedCandidateSha256, ledger.results()); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid assurance gates", exception); }
        if (ledger.references().size() > 128) { throw new IOException("Too many evidence references"); }
        var references = new HashMap<String, Reference>();
        for (Reference reference : ledger.references()) {
            if (references.putIfAbsent(reference.path(), reference) != null) {
                throw new IOException("Repeated evidence reference");
            }
        }
        var used = new HashSet<String>();
        for (var result : summary.results()) {
            if (!result.evidence().isEmpty()) {
                if (!references.containsKey(result.evidence())) { throw new IOException("Missing hashed evidence reference"); }
                used.add(result.evidence());
            }
        }
        if (!used.equals(references.keySet())) { throw new IOException("Unused evidence reference"); }
        Path root = java.util.Objects.requireNonNull(file.getParent());
        long total = 0;
        for (Reference reference : ledger.references()) {
            if (reference.path().contains("\\") || reference.path().contains(":")) {
                throw new IOException("Evidence requires portable relative paths");
            }
            Path relative = Path.of(reference.path());
            Path evidence = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !evidence.startsWith(root)) {
                throw new IOException("Evidence path escapes ledger directory");
            }
            safeFile(evidence);
            var digest = digest();
            try (var input = Files.newInputStream(evidence)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_EVIDENCE_BYTES) { throw new IOException("Evidence byte budget exceeded"); }
                    digest.update(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(reference.sha256())) {
                throw new IOException("Evidence hash mismatch: " + reference.path());
            }
        }
        return summary;
    }

    private static void safeFile(Path file) throws IOException {
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) { throw new IOException("Linked ledger/evidence paths require review"); }
        }
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().equals(file)) {
            throw new IOException("Ledger/evidence must be regular canonical files");
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
