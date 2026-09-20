package com.ssmt.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Validates reproducible build inputs and outputs without claiming source equivalence. */
public final class BuildEvidenceReader {
    private static final int MAX_RECORD_BYTES = 1024 * 1024;
    private static final long MAX_REFERENCED_BYTES = 256L * 1024 * 1024;

    /** Explicit authority disposition for one independently reviewed relationship. */
    public enum AuthorityDisposition { VERIFIED, REVIEW_REQUIRED, NOT_APPLICABLE }

    /** Source, compiled-JAR, or loader/provider authority decision. */
    public record Authority(String subject, AuthorityDisposition disposition, String reason) {
        public Authority {
            requireText(subject, "Authority subject");
            Objects.requireNonNull(disposition, "disposition");
            requireText(reason, "Authority reason");
        }
    }

    /** One contained regular file included in the build record. */
    public record FileReference(String path, String sha256) {
        public FileReference {
            if (path == null || path.isBlank() || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid build file reference");
            }
        }
    }

    /** Exact observed build context, bound to the candidate input bytes. */
    public record Profile(int schemaVersion, String candidateSha256, String jdkExecutable,
            String jdkVersion, List<String> command, String workingDirectory, int processExitCode,
            List<FileReference> buildInputs, List<FileReference> classpath,
            List<FileReference> outputs, List<Authority> authorities) {
        public Profile {
            if (schemaVersion != 1) { throw new IllegalArgumentException("Unsupported build evidence schema"); }
            requireHash(candidateSha256);
            requireText(jdkExecutable, "JDK executable");
            requireText(jdkVersion, "JDK version");
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            if (command.isEmpty()) { throw new IllegalArgumentException("Exact build command is required"); }
            command.forEach(argument -> requireText(argument, "Build command argument"));
            requireText(workingDirectory, "Build working directory");
            buildInputs = List.copyOf(Objects.requireNonNull(buildInputs, "buildInputs"));
            classpath = List.copyOf(Objects.requireNonNull(classpath, "classpath"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
            authorities = List.copyOf(Objects.requireNonNull(authorities, "authorities"));
            if (buildInputs.isEmpty() || outputs.isEmpty()) {
                throw new IllegalArgumentException("Build evidence requires inputs and outputs");
            }
            var subjects = new HashSet<String>();
            for (Authority authority : authorities) {
                if (!subjects.add(authority.subject())) {
                    throw new IllegalArgumentException("Repeated authority subject: " + authority.subject());
                }
            }
            if (!subjects.containsAll(List.of("SOURCE", "COMPILED_JAR", "LOADER_PROVIDER"))) {
                throw new IllegalArgumentException("Source, compiled JAR, and loader/provider authority are required");
            }
        }
    }

    /** Reads the record and independently hashes every bounded, contained referenced file. */
    public Profile read(Path profileFile, String expectedCandidateSha256) throws IOException {
        Path file = profileFile.toAbsolutePath().normalize();
        safeFile(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_RECORD_BYTES + 1); }
        if (bytes.length > MAX_RECORD_BYTES) { throw new IOException("Build evidence exceeds 1 MiB"); }
        Profile profile;
        try {
            profile = JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
                    .readValue(bytes, Profile.class);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid build evidence", exception);
        }
        if (profile == null || !profile.candidateSha256().equals(expectedCandidateSha256)) {
            throw new IOException("Build evidence belongs to a different candidate");
        }
        var paths = new HashSet<String>();
        long total = 0;
        for (FileReference reference : allReferences(profile)) {
            if (!paths.add(reference.path())) {
                throw new IOException("Repeated build file reference: " + reference.path());
            }
            Path referenced = resolveContained(Objects.requireNonNull(file.getParent()), reference.path());
            safeFile(referenced);
            var digest = digest();
            try (var input = Files.newInputStream(referenced)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_REFERENCED_BYTES) {
                        throw new IOException("Build evidence byte budget exceeded");
                    }
                    digest.update(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(reference.sha256())) {
                throw new IOException("Build file hash mismatch: " + reference.path());
            }
        }
        return profile;
    }

    /** Rejects mechanically contradictory success claims after structural/hash validation. */
    public void verifySuccessful(Profile profile) throws IOException {
        if (profile.processExitCode() != 0) {
            throw new IOException("Passing build evidence requires process exit code 0");
        }
        for (Authority authority : profile.authorities()) {
            if (authority.disposition() != AuthorityDisposition.VERIFIED) {
                throw new IOException("Passing build evidence requires VERIFIED authority: "
                        + authority.subject());
            }
        }
    }

    private static List<FileReference> allReferences(Profile profile) {
        var references = new java.util.ArrayList<FileReference>();
        references.addAll(profile.buildInputs());
        references.addAll(profile.classpath());
        references.addAll(profile.outputs());
        return references;
    }

    private static Path resolveContained(Path root, String name) throws IOException {
        if (name.contains("\\") || name.contains(":")) {
            throw new IOException("Build files require portable relative paths");
        }
        Path relative = Path.of(name);
        Path resolved = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !resolved.startsWith(root)) {
            throw new IOException("Build file path escapes evidence directory");
        }
        return resolved;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
    }

    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Candidate SHA-256 must be 64 lowercase hex characters");
        }
    }

    private static void safeFile(Path file) throws IOException {
        for (Path current = file; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) { throw new IOException("Linked build evidence paths require review"); }
        }
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Build evidence must use regular files");
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
