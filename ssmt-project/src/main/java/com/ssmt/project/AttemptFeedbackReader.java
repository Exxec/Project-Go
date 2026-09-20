package com.ssmt.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Strict feedback record for converting revival surprises into reusable coverage. */
public final class AttemptFeedbackReader {
    private static final int MAX_RECORD_BYTES = 1024 * 1024;
    private static final long MAX_REFERENCED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_REFERENCES = 256;

    /** Required classification for every unexpected result. */
    public enum Category { CANDIDATE_SPECIFIC, DETECTOR_GAP, WORKFLOW_GAP, DOCUMENTATION_GAP }

    /** One contained, hash-bound evidence, fixture, or change artifact. */
    public record Reference(String path, String sha256) {
        public Reference {
            if (path == null || path.isBlank() || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid feedback reference");
            }
        }
    }

    /** One classified surprise and the three artifacts required to close it. */
    public record Surprise(String id, Category category, String summary,
            String evidence, String fixture, String change) {
        public Surprise {
            requireText(id, "Surprise ID");
            Objects.requireNonNull(category, "category");
            requireText(summary, "Surprise summary");
            requireText(evidence, "Surprise evidence");
            requireText(fixture, "Reusable fixture");
            requireText(change, "Tool or documentation change");
            if (new HashSet<>(List.of(evidence, fixture, change)).size() != 3) {
                throw new IllegalArgumentException(
                        "Evidence, fixture, and change must be distinct artifacts");
            }
        }
    }

    /** Completion is a reviewed declaration; readiness is derived by the reader. */
    public record Record(int schemaVersion, String candidateSha256, String attemptId,
            boolean reviewComplete, List<Surprise> surprises, List<Reference> references) {
        public Record {
            if (schemaVersion != 1) { throw new IllegalArgumentException("Unsupported feedback schema"); }
            requireHash(candidateSha256);
            requireText(attemptId, "Attempt ID");
            surprises = List.copyOf(Objects.requireNonNull(surprises, "surprises"));
            references = List.copyOf(Objects.requireNonNull(references, "references"));
        }
    }

    /** Verified record plus a derived next-attempt disposition. */
    public record Checked(Record record, String status) { }

    /** Reads a candidate-bound record and verifies every referenced artifact. */
    public Checked read(Path recordFile, String expectedCandidateSha256) throws IOException {
        Path file = recordFile.toAbsolutePath().normalize();
        safeFile(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_RECORD_BYTES + 1); }
        if (bytes.length > MAX_RECORD_BYTES) { throw new IOException("Feedback record exceeds 1 MiB"); }
        Record record = JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
                .readValue(bytes, Record.class);
        if (record == null || !record.candidateSha256().equals(expectedCandidateSha256)) {
            throw new IOException("Feedback belongs to a different candidate");
        }
        var surpriseIds = new HashSet<String>();
        var required = new HashSet<String>();
        for (Surprise surprise : record.surprises()) {
            if (!surpriseIds.add(surprise.id())) {
                throw new IOException("Repeated surprise ID: " + surprise.id());
            }
            required.add(surprise.evidence());
            required.add(surprise.fixture());
            required.add(surprise.change());
        }
        if (record.references().size() > MAX_REFERENCES) {
            throw new IOException("Too many feedback references");
        }
        var references = new HashMap<String, Reference>();
        for (Reference reference : record.references()) {
            if (references.putIfAbsent(reference.path(), reference) != null) {
                throw new IOException("Repeated feedback reference: " + reference.path());
            }
        }
        if (!required.equals(references.keySet())) {
            throw new IOException("Feedback references must exactly cover evidence, fixture, and change artifacts");
        }
        verifyReferences(Objects.requireNonNull(file.getParent()), record.references());
        return new Checked(record, record.reviewComplete()
                ? "READY_FOR_NEXT_CANDIDATE" : "REVIEW_PENDING");
    }

    private static void verifyReferences(Path root, List<Reference> references) throws IOException {
        long total = 0;
        for (Reference reference : references) {
            Path artifact = resolveContained(root, reference.path());
            safeFile(artifact);
            MessageDigest digest = digest();
            try (var input = Files.newInputStream(artifact)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_REFERENCED_BYTES) {
                        throw new IOException("Feedback artifact byte budget exceeded");
                    }
                    digest.update(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(reference.sha256())) {
                throw new IOException("Feedback artifact hash mismatch: " + reference.path());
            }
        }
    }

    private static Path resolveContained(Path root, String name) throws IOException {
        if (name.contains("\\") || name.contains(":")) {
            throw new IOException("Feedback artifacts require portable relative paths");
        }
        Path relative = Path.of(name);
        Path artifact = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !artifact.startsWith(root)) {
            throw new IOException("Feedback artifact escapes record directory");
        }
        return artifact;
    }

    private static void safeFile(Path file) throws IOException {
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Linked feedback paths require review");
            }
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().equals(file)) {
            throw new IOException("Feedback artifacts must be regular canonical files");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
    }

    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Candidate SHA-256 must be 64 lowercase hex characters");
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
