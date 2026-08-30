package com.ssmt.patcher;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * Stages and publishes pristine and translated source clones.
 */
public final class PatchBuilder {
    private static final String CACHE_FILE = ".ssmt-build-fingerprint";
    private final Publisher publisher;

    /** Creates a publisher backed by atomic moves where supported. */
    public PatchBuilder() {
        this(PatchBuilder::publishPath);
    }

    PatchBuilder(Publisher publisher) {
        this.publisher = publisher;
    }

    /** Returns the deterministic pristine-clone sibling for a translated output. */
    public static Path sourceBackupRoot(Path outputRoot) {
        Path output = outputRoot.toAbsolutePath().normalize();
        return output.resolveSibling(output.getFileName() + "-source-backup");
    }

    /**
     * Builds pristine and translated clones without writing to the source tree.
     *
     * @param request validated build request
     * @throws PatchBuilderException on filesystem or publication failure
     */
    public PatchBuildResult build(PatchRequest request) throws PatchBuilderException {
        Path output = request.outputRoot();
        Path parent = output.getParent();
        if (parent == null) {
            throw new PatchBuilderException("Output must have a parent directory");
        }
        Path sourceBackup = sourceBackupRoot(output);
        Path translatedStaging = parent.resolve("." + output.getFileName()
                + ".ssmt-translated-staging");
        Path sourceStaging = parent.resolve("." + output.getFileName()
                + ".ssmt-source-staging");
        Path previousTranslated = parent.resolve("." + output.getFileName()
                + ".ssmt-previous-translated");
        Path previousSource = parent.resolve("." + output.getFileName()
                + ".ssmt-previous-source");
        Path fingerprintFile = output.resolve(CACHE_FILE);
        String fingerprint = fingerprint(request);
        try {
            if (Files.isDirectory(output)
                    && Files.isDirectory(sourceBackup)
                    && Files.isRegularFile(fingerprintFile)
                    && Files.readString(fingerprintFile, StandardCharsets.UTF_8)
                            .equals(fingerprint)) {
                return new PatchBuildResult(false, request.artifacts().size());
            }
            deleteTree(translatedStaging);
            deleteTree(sourceStaging);
            deleteTree(previousTranslated);
            deleteTree(previousSource);
            copyTree(request.sourceRoot(), sourceStaging, true);
            copyTree(sourceStaging, translatedStaging, false);
            for (PatchArtifact artifact : request.artifacts()) {
                Path destination = translatedStaging.resolve(artifact.relativePath()).normalize();
                if (!destination.startsWith(translatedStaging)) {
                    throw new PatchBuilderException(
                            "Artifact escapes staging root: " + artifact.relativePath());
                }
                Path destinationParent = destination.getParent();
                if (destinationParent == null) {
                    throw new PatchBuilderException(
                            "Artifact has no output parent: " + artifact.relativePath());
                }
                Files.createDirectories(destinationParent);
                Files.write(destination, artifact.content());
            }
            if (!fingerprint(request).equals(fingerprint)) {
                throw new PatchBuilderException(
                        "Source mod changed while clone publication was being staged");
            }
            Files.writeString(
                    translatedStaging.resolve(CACHE_FILE),
                    fingerprint,
                    StandardCharsets.UTF_8);
            replacePair(
                    output,
                    sourceBackup,
                    translatedStaging,
                    sourceStaging,
                    previousTranslated,
                    previousSource);
            deleteTree(previousTranslated);
            deleteTree(previousSource);
            return new PatchBuildResult(true, request.artifacts().size());
        } catch (PatchBuilderException exception) {
            cleanup(translatedStaging, exception);
            cleanup(sourceStaging, exception);
            throw exception;
        } catch (IOException exception) {
            PatchBuilderException failure =
                    new PatchBuilderException("Could not build translated clone at "
                            + output, exception);
            cleanup(translatedStaging, failure);
            cleanup(sourceStaging, failure);
            throw failure;
        }
    }

    private static void copyTree(Path source, Path destination, boolean preserveAttributes)
            throws IOException, PatchBuilderException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                Path target = destination.resolve(relative).normalize();
                if (!target.startsWith(destination)) {
                    throw new PatchBuilderException("Source path escapes clone root: " + relative);
                }
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new PatchBuilderException(
                            "Source clone does not support links or special files: " + relative);
                }
                if (attributes.isDirectory()) {
                    Files.createDirectories(target);
                } else if (attributes.isRegularFile()) {
                    if (preserveAttributes) {
                        Files.copy(
                                path,
                                target,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                    } else {
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    static void publishPath(Path staging, Path output) throws IOException {
        try {
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(staging, output);
        }
    }

    private static String fingerprint(PatchRequest request) throws PatchBuilderException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, request.patchId());
            update(digest, request.patchName());
            update(digest, request.sourceModId());
            update(digest, request.sourceModName());
            update(digest, request.sourceGameVersion() == null ? "" : request.sourceGameVersion());
            try (var paths = Files.walk(request.sourceRoot())) {
                for (Path path : paths.sorted().toList()) {
                    BasicFileAttributes attributes = Files.readAttributes(
                            path,
                            BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    Path relative = request.sourceRoot().relativize(path);
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw new PatchBuilderException(
                                "Source clone does not support links or special files: "
                                        + relative);
                    }
                    update(digest, relative.toString().replace('\\', '/'));
                    if (attributes.isDirectory()) {
                        update(digest, "directory");
                    } else if (attributes.isRegularFile()) {
                        byte[] content = Files.readAllBytes(path);
                        updateLength(digest, content.length);
                        digest.update(content);
                    }
                }
            }
            for (PatchArtifact artifact : request.artifacts()) {
                update(digest, artifact.relativePath().toString().replace('\\', '/'));
                byte[] content = artifact.content();
                updateLength(digest, content.length);
                digest.update(content);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new PatchBuilderException("Could not fingerprint clone inputs", exception);
        }
    }

    private void replacePair(
            Path output,
            Path sourceBackup,
            Path translatedStaging,
            Path sourceStaging,
            Path previousTranslated,
            Path previousSource) throws IOException {
        try {
            if (Files.exists(output)) {
                Files.move(output, previousTranslated);
            }
            if (Files.exists(sourceBackup)) {
                Files.move(sourceBackup, previousSource);
            }
            publisher.publish(sourceStaging, sourceBackup);
            publisher.publish(translatedStaging, output);
        } catch (IOException exception) {
            rollbackPublishedPair(
                    output, sourceBackup, previousTranslated, previousSource, exception);
            throw exception;
        }
    }

    private void rollbackPublishedPair(
            Path output,
            Path sourceBackup,
            Path previousTranslated,
            Path previousSource,
            IOException failure) {
        try {
            deleteTree(output);
            deleteTree(sourceBackup);
            if (Files.exists(previousTranslated)) {
                publisher.publish(previousTranslated, output);
            }
            if (Files.exists(previousSource)) {
                publisher.publish(previousSource, sourceBackup);
            }
        } catch (IOException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateLength(digest, bytes.length);
        digest.update(bytes);
    }

    @FunctionalInterface
    interface Publisher {
        void publish(Path staging, Path output) throws IOException;
    }

    private static void updateLength(MessageDigest digest, int length) {
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
    }

    private static void cleanup(Path staging, PatchBuilderException failure) {
        try {
            deleteTree(staging);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
