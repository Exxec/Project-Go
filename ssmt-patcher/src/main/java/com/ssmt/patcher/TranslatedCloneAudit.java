package com.ssmt.patcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Independently verifies a staged translated clone before publication.
 *
 * <p>Every source file must be reproduced byte-for-byte unless an explicit
 * patch artifact replaces it. Generated files are separately declared; an
 * undeclared output file is never silently accepted.</p>
 */
public final class TranslatedCloneAudit {
    /** Deterministic attestation result for one source/staged-clone comparison. */
    public record Result(List<String> preserved, List<String> translated, List<String> missing,
            List<String> unexpected, List<String> incorrect) {
        public Result {
            preserved = List.copyOf(preserved);
            translated = List.copyOf(translated);
            missing = List.copyOf(missing);
            unexpected = List.copyOf(unexpected);
            incorrect = List.copyOf(incorrect);
        }

        /** True only when every output path and byte sequence was declared. */
        public boolean valid() {
            return missing.isEmpty() && unexpected.isEmpty() && incorrect.isEmpty();
        }
    }

    /** Audits the clone without writing either tree. */
    public Result verify(Path source, Path clone, List<PatchArtifact> artifacts,
            Map<Path, byte[]> generated) throws IOException {
        Map<String, byte[]> expected = expected(artifacts, generated);
        Map<String, byte[]> sourceFiles = files(source);
        Map<String, byte[]> cloneFiles = files(clone);
        List<String> preserved = new ArrayList<>();
        List<String> translated = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        List<String> incorrect = new ArrayList<>();

        for (var sourceEntry : sourceFiles.entrySet()) {
            String path = sourceEntry.getKey();
            byte[] cloneBytes = cloneFiles.remove(path);
            byte[] expectedBytes = expected.remove(path);
            if (cloneBytes == null) {
                missing.add(path);
            } else if (expectedBytes != null) {
                if (Arrays.equals(expectedBytes, cloneBytes)) {
                    translated.add(path);
                } else {
                    incorrect.add(path);
                }
            } else if (Arrays.equals(sourceEntry.getValue(), cloneBytes)) {
                preserved.add(path);
            } else {
                incorrect.add(path);
            }
        }
        for (var expectedEntry : expected.entrySet()) {
            byte[] cloneBytes = cloneFiles.remove(expectedEntry.getKey());
            if (cloneBytes == null || !Arrays.equals(expectedEntry.getValue(), cloneBytes)) {
                incorrect.add(expectedEntry.getKey());
            } else {
                translated.add(expectedEntry.getKey());
            }
        }
        unexpected.addAll(cloneFiles.keySet());
        return new Result(preserved, translated, missing, unexpected, incorrect);
    }

    private static Map<String, byte[]> expected(List<PatchArtifact> artifacts,
            Map<Path, byte[]> generated) {
        Map<String, byte[]> expected = new TreeMap<>();
        for (PatchArtifact artifact : artifacts) {
            put(expected, path(artifact.relativePath()), artifact.content());
        }
        for (var generatedEntry : generated.entrySet()) {
            put(expected, path(generatedEntry.getKey()), generatedEntry.getValue());
        }
        return expected;
    }

    private static void put(Map<String, byte[]> target, String path, byte[] bytes) {
        if (target.put(path, bytes.clone()) != null) {
            throw new IllegalArgumentException("Duplicate declared clone output: " + path);
        }
    }

    private static Map<String, byte[]> files(Path root) throws IOException {
        Path canonical = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Map<String, byte[]> files = new TreeMap<>();
        try (var paths = Files.walk(canonical)) {
            for (Path path : paths.toList()) {
                if (path.equals(canonical)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Clone audit does not follow symbolic links: " + path);
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    files.put(path(canonical.relativize(path)), Files.readAllBytes(path));
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Clone audit encountered unsupported path: " + path);
                }
            }
        }
        return files;
    }

    private static String path(Path relative) {
        Path normalized = relative.normalize();
        if (relative.isAbsolute() || normalized.getNameCount() == 0 || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Clone audit path must be safe and relative");
        }
        return normalized.toString().replace('\\', '/');
    }
}
