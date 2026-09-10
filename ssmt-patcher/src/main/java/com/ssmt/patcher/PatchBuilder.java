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
    private static final System.Logger LOG = System.getLogger(PatchBuilder.class.getName());
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
        return build(request, true);
    }

    /**
     * Builds one translated copy without publishing a redundant pristine sibling.
     * The source remains the pristine input; prior output is retained only while
     * an atomic-style replacement is in progress.
     *
     * @param request validated build request
     * @return publication result
     * @throws PatchBuilderException on filesystem or publication failure
     */
    public PatchBuildResult buildTranslatedCopy(PatchRequest request)
            throws PatchBuilderException {
        return build(request, false);
    }

    private PatchBuildResult build(PatchRequest request, boolean publishSourceBackup)
            throws PatchBuilderException {
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
        // Validate before entering cleanup scope: an invalid staging path may be the source itself.
        for (Path managed : java.util.List.of(translatedStaging, sourceStaging, previousTranslated, previousSource)) {
                if (managed.startsWith(request.sourceRoot()) || request.sourceRoot().startsWith(managed)) {
                    throw new PatchBuilderException("Build staging and source roots must not overlap");
                }
        }
        try {
            if (Files.exists(previousTranslated) || Files.exists(previousSource)) {
                throw new PatchBuilderException("Previous build recovery data remains; preserve it before retrying publication");
            }
            if (Files.isDirectory(output)
                    && (!publishSourceBackup || Files.isDirectory(sourceBackup))
                    && Files.isRegularFile(fingerprintFile)
                    && Files.readString(fingerprintFile, StandardCharsets.UTF_8)
                            .equals(fingerprint)
                    && artifactsMatch(output, request)) {
                return new PatchBuildResult(false, request.artifacts().size());
            }
            deleteTree(translatedStaging);
            deleteTree(sourceStaging);
            if (publishSourceBackup) {
                copyTree(request.sourceRoot(), sourceStaging, true);
                copyTree(sourceStaging, translatedStaging, false);
            } else {
                copyTree(request.sourceRoot(), translatedStaging, true);
            }
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
            if (publishSourceBackup) {
                replacePair(
                        output,
                        sourceBackup,
                        translatedStaging,
                        sourceStaging,
                        previousTranslated,
                        previousSource);
            } else {
                replaceSingle(output, translatedStaging, previousTranslated);
            }
            // Publication has committed. Cleanup must not turn success into an apparent rollback.
            try { deleteTree(previousTranslated); }
            catch (IOException exception) { LOG.log(System.Logger.Level.WARNING, "Published output; prior translated recovery data remains", exception); }
            try { deleteTree(previousSource); }
            catch (IOException exception) { LOG.log(System.Logger.Level.WARNING, "Published output; prior source recovery data remains", exception); }
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

    private void replaceSingle(
            Path output, Path translatedStaging, Path previousTranslated) throws IOException {
        boolean movedTranslated = false;
        boolean attemptedTranslated = false;
        try {
            if (Files.exists(output)) {
                Files.move(output, previousTranslated);
                movedTranslated = true;
            }
            attemptedTranslated = true;
            publisher.publish(translatedStaging, output);
        } catch (IOException exception) {
            if (movedTranslated || attemptedTranslated) {
                restore(output, previousTranslated, movedTranslated, exception);
            }
            throw exception;
        }
    }

    private static boolean artifactsMatch(Path output, PatchRequest request) throws IOException {
        for (PatchArtifact artifact : request.artifacts()) {
            Path file = output.resolve(artifact.relativePath());
            if (!Files.isRegularFile(file) || !java.util.Arrays.equals(Files.readAllBytes(file), artifact.content())) {
                return false;
            }
        }
        return true;
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
        boolean movedTranslated = false;
        boolean movedSource = false;
        boolean attemptedSource = false;
        boolean attemptedTranslated = false;
        try {
            if (Files.exists(output)) {
                Files.move(output, previousTranslated);
                movedTranslated = true;
            }
            if (Files.exists(sourceBackup)) {
                Files.move(sourceBackup, previousSource);
                movedSource = true;
            }
            attemptedSource = true;
            publisher.publish(sourceStaging, sourceBackup);
            attemptedTranslated = true;
            publisher.publish(translatedStaging, output);
        } catch (IOException exception) {
            if (movedTranslated || attemptedTranslated) { restore(output, previousTranslated, movedTranslated, exception); }
            if (movedSource || attemptedSource) { restore(sourceBackup, previousSource, movedSource, exception); }
            throw exception;
        }
    }

    private void restore(Path output, Path previous, boolean existed, IOException failure) {
        try {
            deleteTree(output);
            if (existed) { publisher.publish(previous, output); }
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
