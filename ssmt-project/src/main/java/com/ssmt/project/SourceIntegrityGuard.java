package com.ssmt.project;

import com.ssmt.scanner.SourceTreeManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Proves observed source bytes and metadata remain unchanged across one operation. */
public final class SourceIntegrityGuard {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Durable successful comparison; one manifest represents equal pre/post nodes. */
    public record Attestation(String operation, String beforeSha256, String afterSha256,
            List<SourceTreeManifest.Node> manifest, String status) {
        public Attestation { manifest = List.copyOf(manifest); }
    }

    /** Operation result paired with its source attestation. */
    public record Attested<T>(T result, Attestation attestation) { }

    /** Operation that may inspect, but must not mutate, the source tree. */
    @FunctionalInterface
    public interface Operation<T> { T run() throws ProjectException; }

    /** Runs an operation between independent pre/post source manifests. */
    public <T> T run(Path source, Operation<T> operation) throws ProjectException {
        return runAttested("UNSPECIFIED", source, operation).result();
    }

    /** Runs and returns a persistable equal-manifest attestation. */
    public <T> Attested<T> runAttested(String name, Path source, Operation<T> operation)
            throws ProjectException {
        List<SourceTreeManifest.Node> before = capture(source);
        T result = null;
        ProjectException failure = null;
        try {
            result = operation.run();
        } catch (ProjectException exception) {
            failure = exception;
        }
        List<SourceTreeManifest.Node> after = capture(source);
        if (!before.equals(after)) {
            var changed = new ProjectException("Source mod changed during the workflow operation");
            if (failure != null) { changed.addSuppressed(failure); }
            throw changed;
        }
        if (failure != null) { throw failure; }
        String beforeHash = manifestSha256(before);
        String afterHash = manifestSha256(after);
        return new Attested<>(result, new Attestation(name, beforeHash, afterHash,
                before, "UNCHANGED_OBSERVED_BYTES_AND_METADATA"));
    }

    private static List<SourceTreeManifest.Node> capture(Path source) throws ProjectException {
        try {
            return new SourceTreeManifest().capture(source);
        } catch (IOException exception) {
            throw new ProjectException("Could not attest source mod bytes and metadata", exception);
        }
    }

    /** Deterministic digest used when durable attestations are re-read. */
    public static String manifestSha256(List<SourceTreeManifest.Node> nodes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsBytes(nodes)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash source attestation", exception);
        }
    }
}
