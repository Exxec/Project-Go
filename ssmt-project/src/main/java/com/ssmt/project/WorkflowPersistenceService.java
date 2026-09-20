package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Recoverable all-or-previous publication for application-owned workflow files. */
public final class WorkflowPersistenceService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TRANSACTION_DIRECTORY = ".workflow-transaction";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_DOCUMENTS = 16;
    private final Publisher publisher;

    /** One target and its complete immutable staged contents. */
    public static final class Update {
        private final Path target;
        private final byte[] contents;

        public Update(Path target, byte[] contents) {
            this.target = Objects.requireNonNull(target, "target");
            this.contents = Objects.requireNonNull(contents, "contents").clone();
        }
    }

    @FunctionalInterface
    public interface Publisher {
        void publish(Path staging, Path target) throws IOException;
    }

    public WorkflowPersistenceService() {
        this(WorkflowPersistenceService::publishAtomically);
    }

    public WorkflowPersistenceService(Publisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /**
     * Publishes every update or restores every previous document. A durable
     * manifest permits the next process to finish recovery after interruption.
     */
    public void commit(Path workspace, List<Update> updates) throws ProjectException {
        Path root = workspace.toAbsolutePath().normalize();
        if (updates == null || updates.isEmpty() || updates.size() > MAX_DOCUMENTS) {
            throw new IllegalArgumentException("updates must contain 1-" + MAX_DOCUMENTS + " documents");
        }
        validateWorkspace(root);
        recover(root);
        List<Path> targets = validateTargets(root, updates);
        Path transaction = root.resolve(TRANSACTION_DIRECTORY);
        List<Entry> entries = new ArrayList<>();
        boolean manifestWritten = false;
        try {
            Files.createDirectories(transaction.resolve("new"));
            Files.createDirectories(transaction.resolve("old"));
            for (int index = 0; index < updates.size(); index++) {
                Path staged = transaction.resolve("new").resolve(index + ".document");
                Files.write(staged, updates.get(index).contents);
                force(staged);
                Path target = targets.get(index);
                boolean existed = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
                String oldHash = "";
                if (existed) {
                    Path backup = transaction.resolve("old").resolve(index + ".document");
                    Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
                    force(backup);
                    oldHash = sha256(backup);
                }
                entries.add(new Entry(root.relativize(target).toString().replace('\\', '/'),
                        existed, oldHash, sha256(staged)));
            }
            writeManifest(transaction.resolve(MANIFEST_FILE), entries);
            manifestWritten = true;
            for (int index = 0; index < targets.size(); index++) {
                publisher.publish(transaction.resolve("new").resolve(index + ".document"),
                        targets.get(index));
            }
            deleteTree(transaction);
        } catch (IOException exception) {
            if (manifestWritten) {
                try {
                    restorePrevious(root, transaction, entries);
                } catch (IOException | ProjectException recoveryFailure) {
                    exception.addSuppressed(recoveryFailure);
                    throw new ProjectException(
                            "Could not save workflow state and automatic rollback needs review",
                            exception);
                }
            }
            try {
                deleteTree(transaction);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw new ProjectException(
                    "Could not save workflow state; previous state was retained", exception);
        }
    }

    /** Recovers a previously interrupted publication before its documents are read. */
    public void recover(Path workspace) throws ProjectException {
        Path root = workspace.toAbsolutePath().normalize();
        validateWorkspace(root);
        Path transaction = root.resolve(TRANSACTION_DIRECTORY);
        if (!Files.exists(transaction, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(transaction, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(transaction)) {
            throw new ProjectException("Workflow transaction path needs manual review");
        }
        Path manifest = transaction.resolve(MANIFEST_FILE);
        if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            try {
                deleteTree(transaction);
                return;
            } catch (IOException exception) {
                throw new ProjectException(
                        "Could not clean an uncommitted workflow transaction", exception);
            }
        }
        try {
            List<Entry> entries = readManifest(manifest);
            List<Path> targets = resolveTargets(root, entries);
            boolean complete = true;
            for (int index = 0; index < entries.size(); index++) {
                Path target = targets.get(index);
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        || !entries.get(index).newHash().equals(sha256(target))) {
                    complete = false;
                }
            }
            if (!complete) {
                restorePrevious(root, transaction, entries);
            }
            deleteTree(transaction);
        } catch (IOException exception) {
            throw new ProjectException("Could not recover interrupted workflow state", exception);
        }
    }

    private static List<Path> validateTargets(Path root, List<Update> updates)
            throws ProjectException {
        Set<Path> unique = new HashSet<>();
        List<Path> targets = new ArrayList<>();
        for (Update update : updates) {
            Path target = update.target.toAbsolutePath().normalize();
            if (!target.startsWith(root) || target.equals(root) || !unique.add(target)) {
                throw new ProjectException("Workflow document target is outside its workspace or repeated");
            }
            Path relative = root.relativize(target);
            Path current = root;
            for (Path component : relative) {
                current = current.resolve(component);
                if (Files.isSymbolicLink(current)) {
                    throw new ProjectException("Workflow document target must not use symbolic links");
                }
            }
            Path parent = target.getParent();
            if (parent == null || !parent.equals(root)) {
                throw new ProjectException("Workflow documents must be direct children of their workspace");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectException("Workflow document target must be a regular file");
            }
            targets.add(target);
        }
        return targets;
    }

    private static List<Path> resolveTargets(Path root, List<Entry> entries)
            throws ProjectException {
        List<Update> synthetic = entries.stream()
                .map(entry -> new Update(root.resolve(entry.target()), new byte[0]))
                .toList();
        return validateTargets(root, synthetic);
    }

    private static void restorePrevious(Path root, Path transaction, List<Entry> entries)
            throws IOException, ProjectException {
        List<Path> targets = resolveTargets(root, entries);
        IOException failure = null;
        for (int index = 0; index < entries.size(); index++) {
            try {
                Path target = targets.get(index);
                Entry entry = entries.get(index);
                if (entry.existed()) {
                    Path backup = transaction.resolve("old").resolve(index + ".document");
                    requireRegularFile(backup, "Workflow rollback document is missing");
                    if (!entry.oldHash().equals(sha256(backup))) {
                        throw new IOException("Workflow rollback document changed");
                    }
                    Path restore = transaction.resolve("restore-" + index + ".document");
                    Files.copy(backup, restore, StandardCopyOption.REPLACE_EXISTING);
                    publishAtomically(restore, target);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException | ProjectException exception) {
                IOException itemFailure = exception instanceof IOException io
                        ? io : new IOException(exception);
                if (failure == null) {
                    failure = itemFailure;
                } else {
                    failure.addSuppressed(itemFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void writeManifest(Path manifest, List<Entry> entries) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode documents = root.putArray("documents");
        for (Entry entry : entries) {
            ObjectNode document = documents.addObject();
            document.put("target", entry.target());
            document.put("existed", entry.existed());
            document.put("oldSha256", entry.oldHash());
            document.put("newSha256", entry.newHash());
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), root);
        force(manifest);
    }

    private static List<Entry> readManifest(Path manifest) throws IOException, ProjectException {
        requireRegularFile(manifest, "Workflow transaction manifest is missing");
        JsonNode root = JSON.readTree(manifest.toFile());
        if (root.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
                || !root.path("documents").isArray()
                || root.path("documents").isEmpty()
                || root.path("documents").size() > MAX_DOCUMENTS) {
            throw new ProjectException("Workflow transaction manifest is invalid");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode document : root.path("documents")) {
            String target = document.path("target").asText("");
            String oldHash = document.path("oldSha256").asText("");
            String newHash = document.path("newSha256").asText("");
            if (target.isBlank() || !newHash.matches("[0-9a-f]{64}")
                    || (!oldHash.isEmpty() && !oldHash.matches("[0-9a-f]{64}"))
                    || document.path("existed").asBoolean() != !oldHash.isEmpty()) {
                throw new ProjectException("Workflow transaction manifest is invalid");
            }
            entries.add(new Entry(target, document.path("existed").asBoolean(),
                    oldHash, newHash));
        }
        return entries;
    }

    private static void requireRegularFile(Path path, String message) throws ProjectException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new ProjectException(message);
        }
    }

    private static void validateWorkspace(Path root) throws ProjectException {
        try {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !root.toRealPath().equals(root)) {
                throw new ProjectException("Workflow workspace must be a canonical directory");
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not resolve workflow workspace", exception);
        }
    }

    private static void publishAtomically(Path staging, Path target) throws IOException {
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Workflow transaction directory became a symbolic link");
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Entry(String target, boolean existed, String oldHash, String newHash) {
    }
}
