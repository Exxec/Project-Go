package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;

/** The source-safe, provider-independent workflow shared by desktop and CLI. */
public final class TranslationWorkflow {
    private static final System.Logger LOG = System.getLogger(TranslationWorkflow.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;
    private final Path inputCache;
    private final LocalizationProjectService projects = new LocalizationProjectService();
    private final AiTranslationExchangeService exchange = new AiTranslationExchangeService();
    private final WorkspacePublisher publisher;

    /** Immutable committed state. Callers replace their active session only after success. */
    public record Session(Path source, Path workspace, LocalizationProject project,
            String modName, String sourceLanguage, String revision, long needsReview) {}

    public TranslationWorkflow() {
        this(Path.of(System.getProperty("projectgo.workspace",
                defaultApplicationRoot().resolve("workspaces").toString())));
    }

    /** Returns the application-owned storage root used by normal workflows. */
    public static Path defaultApplicationRoot() {
        return Path.of(System.getProperty("projectgo.data",
                Path.of(System.getProperty("user.home"), ".project-go").toString()));
    }

    public TranslationWorkflow(Path root) {
        this(root, (staging, target) -> Files.move(staging, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    TranslationWorkflow(Path root, WorkspacePublisher publisher) {
        this.root = root.toAbsolutePath().normalize();
        this.inputCache = this.root.resolveSibling("input-cache");
        this.publisher = publisher;
    }

    /** Accepts a mod folder, mod_info.json, or ZIP and opens its normalized mod root. */
    public Session loadInput(Path input) throws ProjectException {
        var prepared = new ModInputPreparationService().prepare(input, inputCache);
        return loadMod(prepared.modRoot());
    }

    /** Finds durable work before extraction, and refreshes it on every load. */
    public Session loadMod(Path source) throws ProjectException {
        Path canonical;
        try {
            canonical = source.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException("Could not open mod directory", exception);
        }
        requireOutsideSource(canonical, root);
        var details = projects.inspectSource(canonical);
        Path workspace = root.resolve(digest(details.modId() + "\nen"));
        requireOutsideSource(canonical, workspace);
        return locked(workspace, () -> {
            Path file = workspace.resolve("project.ssmt.json");
            ObjectNode metadata = Files.exists(file) ? readState(file) : JSON.createObjectNode();
            LocalizationProject previous = Files.exists(file) ? projects.read(file) : discoverLegacy(canonical, details.modId());
            LocalizationProject candidate;
            if (previous == null) {
                candidate = projects.create(canonical, details.modId() + ".translation", details.modName() + " Translation");
            } else {
                var refreshed = projects.refresh(canonical, previous);
                retainHistory(metadata, previous, refreshed);
                candidate = refreshed.project();
            }
            return commit(canonical, workspace, candidate, details.modName(), metadata);
        });
    }

    /** Writes exactly one self-contained AI JSON; no provider or translation memory is needed. */
    public void exportTranslation(Session session, Path destination) throws ProjectException {
        requireOutsideSource(session.source(), destination);
        requireOutsideWorkspace(session.workspace(), destination);
        locked(session.workspace(), () -> {
            requireCurrent(session);
            exchange.exportPackage(destination, session.project(), session.modName(), session.sourceLanguage(), "en");
            return null;
        });
    }

    /** Validates first, commits second; failures leave the caller's session and disk unchanged. */
    public Session importTranslation(Session session, Path response) throws ProjectException {
        return locked(session.workspace(), () -> {
            requireCurrent(session);
            ObjectNode metadata = readState(session.workspace().resolve("project.ssmt.json"));
            var refreshed = projects.refresh(session.source(), session.project());
            var imported = exchange.importResponse(response, refreshed.project(), null);
            retainHistory(metadata, session.project(), refreshed);
            // A newly supplied translation resolves that entry's pending review flag.
            var pending = metadata.withArray("pendingReview");
            for (int index = pending.size() - 1; index >= 0; index--) {
                JsonNode finding = pending.get(index);
                if (imported.project().entries().stream().anyMatch(e ->
                        e.sourceFile().toString().replace('\\', '/').equals(finding.path("sourceFile").asText())
                        && e.key().equals(finding.path("key").asText()) && !e.translatedText().isBlank())) {
                    pending.remove(index);
                }
            }
            return commit(session.source(), session.workspace(), imported.project(), session.modName(), metadata);
        });
    }

    /** Initially publishes the proven translated clone; standalone patch output is a separate gate. */
    public ProjectBuildResult buildPatch(Session session, Path destination) throws ProjectException {
        requireOutsideSource(session.source(), destination);
        requireOutsideWorkspace(session.workspace(), destination);
        return locked(session.workspace(), () -> {
            requireCurrent(session);
            var current = projects.refresh(session.source(), session.project()).project();
            long missing = current.entries().stream().filter(e -> !e.originalText().isBlank()
                    && e.translatedText().isBlank()).count();
            if (missing > 0) {
                throw new ProjectException(missing
                    + " texts still need translation. Export the translation file, finish it, and import it before building.");
            }
            return projects.buildTranslatedCopy(session.source(), destination, current);
        });
    }

    private LocalizationProject discoverLegacy(Path source, String modId) throws ProjectException {
        // Legacy GUI/Auto workspaces were siblings of the mod, never inside its source tree.
        var candidates = new ArrayList<Path>();
        try (var siblings = Files.list(source.getParent())) {
            for (Path directory : siblings.filter(Files::isDirectory)
                    .filter(p -> java.util.Objects.requireNonNull(p.getFileName()).toString().startsWith("Project Go - ")).toList()) {
                try (var files = Files.list(directory)) {
                    for (Path file : files.filter(p -> java.util.Objects.requireNonNull(p.getFileName()).toString().endsWith(".ssmt.json")).toList()) {
                        if (projects.read(file).sourceModId().equals(modId)) { candidates.add(file); }
                    }
                }
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not discover existing translation projects", exception);
        }
        if (candidates.size() > 1) {
            throw new ProjectException("More than one existing translation project matches this mod: " + candidates);
        }
        return candidates.isEmpty() ? null : projects.read(candidates.getFirst());
    }

    private static void retainHistory(ObjectNode state, LocalizationProject previous, ProjectRefreshResult refreshed) {
        var history = state.withArray("history");
        var pending = state.withArray("pendingReview");
        for (var finding : refreshed.report().entries()) {
            if (finding.status() != ReconciliationStatus.UNCHANGED && finding.status() != ReconciliationStatus.ADDED) {
                ObjectNode record = JSON.createObjectNode();
                record.put("sourceFile", finding.sourceFile().toString().replace('\\', '/'));
                record.put("key", finding.key());
                record.put("status", finding.status().name());
                record.put("source", finding.originalText());
                record.put("previousTranslation", finding.previousTranslation());
                previous.entries().stream().filter(e -> e.sourceFile().equals(finding.sourceFile())
                        && e.key().equals(finding.key())).findFirst().ifPresent(old -> {
                            record.put("previousSource", old.originalText());
                            record.put("previousProvenance", old.provenance().name());
                        });
                record.set("suggestions", JSON.valueToTree(finding.suggestions()));
                if (!contains(history, record)) { history.add(record); }
                // Pending review represents current identities; history retains every old finding.
                for (int index = pending.size() - 1; index >= 0; index--) {
                    JsonNode item = pending.get(index);
                    if (item.path("sourceFile").equals(record.path("sourceFile"))
                            && item.path("key").equals(record.path("key"))) { pending.remove(index); }
                }
                if (finding.status() != ReconciliationStatus.REMOVED) { pending.add(record); }
            }
        }
    }

    private static boolean contains(JsonNode array, JsonNode value) {
        for (JsonNode item : array) {
            if (item.equals(value)) { return true; }
        }
        return false;
    }

    private Session commit(Path source, Path workspace, LocalizationProject candidate,
            String modName, ObjectNode metadata) throws ProjectException {
        Path staging = null;
        try {
            staging = Files.createTempFile(workspace, ".candidate-", ".json");
            projects.write(staging, candidate);
            ObjectNode document = (ObjectNode) JSON.readTree(staging.toFile());
            metadata.put("workspaceVersion", 1);
            metadata.put("sourceRoot", source.toString());
            metadata.put("modName", modName);
            metadata.put("sourceLanguage", new SourceLanguageDetector().detect(candidate.entries()));
            // Embed metadata with the project: a single publication commits both together.
            document.set("workspace", metadata);
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), document);
            String committedRevision = revision(staging);
            Path target = workspace.resolve("project.ssmt.json");
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE)) { channel.force(true); }
            publisher.publish(staging, target);
            return new Session(source, workspace, candidate, modName,
                    metadata.path("sourceLanguage").asText(), committedRevision, metadata.path("pendingReview").size());
        } catch (IOException exception) {
            throw new ProjectException("Could not save translation workspace; previous state was retained", exception);
        } finally {
            if (staging != null) {
                try { Files.deleteIfExists(staging); }
                catch (IOException exception) {
                    LOG.log(System.Logger.Level.WARNING, "Workspace staging cleanup failed", exception);
                }
            }
        }
    }

    private static ObjectNode readState(Path file) throws ProjectException {
        try {
            JsonNode metadata = JSON.readTree(file.toFile()).path("workspace");
            if (metadata.path("workspaceVersion").asInt(-1) != 1) {
                throw new ProjectException("Unsupported or missing translation workspace version");
            }
            return ((ObjectNode) metadata).deepCopy();
        } catch (IOException exception) {
            throw new ProjectException("Could not read translation workspace", exception);
        }
    }

    private static void requireCurrent(Session session) throws ProjectException {
        if (!revision(session.workspace().resolve("project.ssmt.json")).equals(session.revision())) {
            throw new ProjectException("Saved translations changed in another window. Choose the mod again before continuing.");
        }
    }

    private static String revision(Path file) throws ProjectException {
        try { return digest(Files.readString(file)); }
        catch (IOException exception) { throw new ProjectException("Could not read saved translations", exception); }
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void requireOutsideSource(Path source, Path destination) throws ProjectException {
        Path target = destination.toAbsolutePath().normalize();
        // Resolve existing ancestors too, so a linked output directory cannot point into a source mod.
        Path ancestor = target;
        while (ancestor != null && !Files.exists(ancestor)) { ancestor = ancestor.getParent(); }
        try {
            source = source.toRealPath();
            if (ancestor != null) { target = ancestor.toRealPath().resolve(ancestor.relativize(target)).normalize(); }
            if (target.startsWith(source) || source.startsWith(target)) {
                throw new ProjectException("Output and source mod must not overlap");
            }
        } catch (IOException exception) { throw new ProjectException("Could not resolve output location", exception); }
    }

    private void requireOutsideWorkspace(Path workspace, Path destination) throws ProjectException {
        requireOutsideSource(root, destination);
    }

    private <T> T locked(Path workspace, Operation<T> operation) throws ProjectException {
        try {
            Files.createDirectories(workspace);
            if (Files.isSymbolicLink(workspace.resolve(".lock"))) {
                throw new ProjectException("Workspace lock must not be a symbolic link");
            }
            try (FileChannel channel = FileChannel.open(workspace.resolve(".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw new ProjectException("This translation workspace is busy. Try again when the other operation finishes.");
                }
                return operation.run();
            }
        } catch (IOException | OverlappingFileLockException exception) {
            throw new ProjectException("Could not lock translation workspace", exception);
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run() throws ProjectException; }

    @FunctionalInterface
    interface WorkspacePublisher { void publish(Path staging, Path target) throws IOException; }
}
