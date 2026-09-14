package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.tm.MasterTranslationLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
import java.util.Optional;
import java.util.Set;

/** The source-safe, provider-independent workflow shared by desktop and CLI. */
public final class TranslationWorkflow {
    private static final System.Logger LOG = System.getLogger(TranslationWorkflow.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;
    private final Path inputCache;
    private final LocalizationProjectService projects = new LocalizationProjectService();
    private final AiTranslationExchangeService exchange = new AiTranslationExchangeService();
    private final WorkspacePublisher publisher;
    private static final int MAX_LEGACY_DIRECTORIES = 128;
    private static final int MAX_LEGACY_PROJECTS = 256;
    private static final long MAX_LEGACY_PROJECT_BYTES = 64L * 1024L * 1024L;
    private static final String PROJECT_FILE = "project.ssmt.json";
    private static final String LINEAGE_FILE = "lineage.json";
    private static final int LINEAGE_SCHEMA_VERSION = 1;
    private static final int MAX_REGISTERED_FORKS = 32;
    private static final long MAX_LINEAGE_BYTES = 1024L * 1024L;
    /** The one language the normal workflow installs; it is never a user setting. */
    static final String TARGET_LANGUAGE = "en";

    /**
     * Immutable committed state. Callers replace their active session only after success.
     *
     * @param source real source mod root, never written
     * @param workspace hidden application-owned workspace
     * @param project committed project state
     * @param identity exact source metadata, never overwritten
     * @param presentation readable names for folders and the AI request file
     * @param sourceLanguage detected source language
     * @param revision committed durable revision
     * @param needsReview refreshed entries needing a decision
     */
    public record Session(Path source, Path workspace, LocalizationProject project,
            SourceModIdentity identity, PresentationNames presentation,
            String sourceLanguage, String revision, long needsReview) {

        /** Returns the source display name exactly as the mod declares it. */
        public String modName() {
            return identity.originalName();
        }
    }

    /** User decision when one mod id maps to two genuinely different sources. */
    public enum LineageChoice {
        /** Keep and grow the translation work already saved for this mod id. */
        USE_PREVIOUS,
        /** Translate this copy as its own work, leaving the previous work intact. */
        START_SEPARATELY
    }

    public TranslationWorkflow() {
        this(Path.of(System.getProperty("projectgo.workspace",
                defaultApplicationRoot().resolve("workspaces").toString())));
    }

    /** Returns the application-owned storage root used by normal workflows. */
    public static Path defaultApplicationRoot() {
        String configured = System.getProperty("projectgo.data", "").strip();
        if (!configured.isEmpty()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return defaultApplicationRoot(
                Optional.ofNullable(System.getenv("LOCALAPPDATA")),
                Path.of(System.getProperty("user.home")));
    }

    /** Uses the same platform-root policy as the shared translation catalog. */
    static Path defaultApplicationRoot(Optional<String> localAppData, Path userHome) {
        return MasterTranslationLibrary.resolve(Optional.empty(), localAppData, userHome)
                .getParent();
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
        return loadInput(input, null);
    }

    /**
     * Accepts a mod folder, mod_info.json, or ZIP and opens its normalized mod root.
     *
     * @param input user-selected mod input
     * @param choice explicit resolution of a same-id/different-source conflict, or null
     * @return committed session
     * @throws ProjectException when the input cannot be prepared or opened
     */
    public Session loadInput(Path input, LineageChoice choice) throws ProjectException {
        var prepared = new ModInputPreparationService().prepare(input, inputCache);
        return loadMod(prepared.modRoot(), choice);
    }

    /** Lists matching projects only in documented legacy sibling workspaces. */
    public List<LegacyProjectCandidate> legacyProjects(Path input) throws ProjectException {
        var prepared = new ModInputPreparationService().prepare(input, inputCache);
        Path source = prepared.modRoot();
        var identity = projects.inspectIdentity(source);
        if (Files.isRegularFile(workspaceFor(identity.originalId()).resolve(PROJECT_FILE),
                LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        return discoverLegacy(legacySearchParent(prepared), identity.originalId());
    }

    /** Copies one explicitly selected legacy project into a new internal workspace. */
    public Session adoptLegacy(Path input, LegacyProjectCandidate approved) throws ProjectException {
        var prepared = new ModInputPreparationService().prepare(input, inputCache);
        Path source = prepared.modRoot();
        Path canonical;
        try {
            canonical = source.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException("Could not open the selected legacy project", exception);
        }
        var identity = projects.inspectIdentity(canonical);
        Path workspace = workspaceFor(identity.originalId());
        if (Files.exists(workspace.resolve(PROJECT_FILE), LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectException(
                    "An internal translation workspace already exists; it was left unchanged");
        }
        LegacyProjectCandidate selected = discoverLegacy(legacySearchParent(prepared),
                identity.originalId()).stream().filter(approved::equals).findFirst().orElseThrow(() ->
                    new ProjectException("The selected legacy project changed after preview or is no longer eligible"));
        requireOutsideSource(canonical, workspace);
        LocalizationProject legacy = projects.read(selected.file());
        if (!fileDigest(selected.file()).equals(selected.sha256())) {
            throw new ProjectException("The selected legacy project changed during adoption");
        }
        return locked(workspace, () -> {
            Path target = workspace.resolve(PROJECT_FILE);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectException(
                        "An internal translation workspace already exists; it was left unchanged");
            }
            ObjectNode metadata = JSON.createObjectNode();
            ObjectNode adoption = metadata.putObject("legacyAdoption");
            adoption.put("sourceFile", selected.file().toString());
            adoption.put("sha256", selected.sha256());
            adoption.put("sourceModId", selected.sourceModId());
            adoption.put("entries", selected.entries());
            adoption.put("translatedEntries", selected.translatedEntries());
            adoption.put("adoptedAt", java.time.Instant.now().toString());
            var refreshed = projects.refresh(canonical, legacy);
            retainHistory(metadata, legacy, refreshed);
            Session session = commit(canonical, workspace, refreshed.project(), identity, metadata);
            recordLineage(workspace, identity, canonical, workspace, refreshed.project(), false);
            return session;
        });
    }

    /** Finds durable work before extraction, and refreshes it on every load. */
    public Session loadMod(Path source) throws ProjectException {
        return loadMod(source, null);
    }

    /**
     * Finds durable work before extraction and refreshes it on every load. Project
     * Go decides whether to create or resume; users never choose between those.
     *
     * <p>Two genuinely different mods that declare the same id are never merged
     * silently. When the saved work shares no source text at all with the selected
     * mod, an explicit {@link LineageChoice} is required.</p>
     *
     * @param source mod root
     * @param choice explicit conflict resolution, or null when no decision was made
     * @return committed session
     * @throws ProjectException when the mod cannot be opened or saved
     */
    public Session loadMod(Path source, LineageChoice choice) throws ProjectException {
        Path canonical;
        try {
            canonical = source.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException("Could not open mod directory", exception);
        }
        requireOutsideSource(canonical, root);
        var identity = projects.inspectIdentity(canonical);
        Path primary = workspaceFor(identity.originalId());
        requireOutsideSource(canonical, primary);
        // Every fork shares this advisory registry. Hold the per-id lock while
        // resolving and updating it, then retain the fork's own revision lock.
        return locked(primary, () -> {
            Path resolved = registeredForkByPath(primary, canonical).orElse(primary);
            return resolved.equals(primary)
                    ? openWorkspace(canonical, identity, primary, resolved, choice)
                    : locked(resolved, () -> openWorkspace(canonical, identity, primary, resolved, choice));
        });
    }

    /** Creates or resumes one workspace, stopping for a choice on a foreign source. */
    private Session openWorkspace(Path canonical, SourceModIdentity identity, Path primary,
            Path workspace, LineageChoice choice) throws ProjectException {
        Path file = workspace.resolve(PROJECT_FILE);
        ObjectNode metadata = Files.exists(file) ? readState(file) : JSON.createObjectNode();
        LocalizationProject previous = Files.exists(file) ? projects.read(file) : null;
        boolean separated = !workspace.equals(primary);
        if (previous == null) {
            LocalizationProject created = projects.create(
                    canonical, identity.originalId(), identity.originalName());
            Session session = commit(canonical, workspace, created, identity, metadata);
            recordLineage(primary, identity, canonical, workspace, created, separated);
            return session;
        }
        var refreshed = projects.refresh(canonical, previous);
        boolean samePathUpdate = canonical.toString().equals(metadata.path("sourceRoot").asText())
                && sharesEntryLocations(previous, refreshed.project());
        if (choice != LineageChoice.USE_PREVIOUS && !samePathUpdate
                && sharesNoSourceText(previous, refreshed.project())) {
            return separateOrResumeFork(canonical, identity, primary, workspace, refreshed.project(), choice);
        }
        retainHistory(metadata, previous, refreshed);
        Session session = commit(canonical, workspace, refreshed.project(), identity, metadata);
        recordLineage(primary, identity, canonical, workspace, refreshed.project(), separated);
        return session;
    }

    /** Resolves a same-id/different-source load into a known fork or an explicit choice. */
    private Session separateOrResumeFork(Path canonical, SourceModIdentity identity, Path primary,
            Path previousWorkspace, LocalizationProject candidate, LineageChoice choice) throws ProjectException {
        // A previously separated copy may simply have been moved or renamed.
        Path known = registeredForkByEntries(primary, candidate).orElse(null);
        // The registry is advisory; recover exact saved fork evidence even if
        // registration was lost or malformed. Never reuse merely matching keys.
        Path exactFork = forkWorkspaceFor(identity.originalId(), candidate);
        if (known == null && matchingSavedFork(exactFork, candidate)) {
            known = exactFork;
        }
        if (known != null) {
            Path selected = known;
            return locked(selected, () -> openWorkspace(canonical, identity, primary, selected, null));
        }
        if (choice != LineageChoice.START_SEPARATELY) {
            LocalizationProject saved = projects.read(previousWorkspace.resolve(PROJECT_FILE));
            ObjectNode state = readState(previousWorkspace.resolve(PROJECT_FILE));
            throw new SourceIdentityConflictException(canonical,
                    state.path("modName").asText(saved.patchName()), identity.originalName(),
                    saved.entries().size(), candidate.entries().size());
        }
        Path fork = forkWorkspaceFor(identity.originalId(), candidate);
        Session session = locked(fork, () -> openWorkspace(canonical, identity, primary, fork, null));
        recordLineage(primary, identity, canonical, fork, session.project(), true);
        return session;
    }

    /** Writes exactly one self-contained AI JSON; no provider or translation memory is needed. */
    public void exportTranslation(Session session, Path destination) throws ProjectException {
        requireOutsideSource(session.source(), destination);
        requireOutsideWorkspace(session.workspace(), destination);
        locked(session.workspace(), () -> {
            requireCurrent(session);
            exchange.exportPackage(destination, session.project(), session.modName(),
                    session.sourceLanguage(), TARGET_LANGUAGE);
            return null;
        });
    }

    /** Validates first, commits second; failures leave the caller's session and disk unchanged. */
    public Session importTranslation(Session session, Path response) throws ProjectException {
        return locked(session.workspace(), () -> {
            requireCurrent(session);
            ObjectNode metadata = readState(session.workspace().resolve(PROJECT_FILE));
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
            return commit(session.source(), session.workspace(), imported.project(), session.identity(), metadata);
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

    private List<LegacyProjectCandidate> discoverLegacy(Path parent, String modId)
            throws ProjectException {
        var candidates = new ArrayList<LegacyProjectCandidate>();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var siblings = Files.list(parent)) {
            int inspectedProjects = 0;
            List<Path> legacyDirectories = siblings
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> java.util.Objects.requireNonNull(path.getFileName())
                            .toString().startsWith("Project Go - "))
                    .limit(MAX_LEGACY_DIRECTORIES + 1L).toList();
            if (legacyDirectories.size() > MAX_LEGACY_DIRECTORIES) {
                throw new ProjectException("Too many legacy Project Go directories to inspect safely");
            }
            for (Path directory : legacyDirectories) {
                try (var files = Files.list(directory)) {
                    List<Path> projectFiles = files
                            .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .filter(path -> !Files.isSymbolicLink(path))
                            .filter(path -> java.util.Objects.requireNonNull(path.getFileName())
                                    .toString().endsWith(".ssmt.json"))
                            .limit(MAX_LEGACY_PROJECTS - inspectedProjects + 1L).toList();
                    inspectedProjects += projectFiles.size();
                    if (inspectedProjects > MAX_LEGACY_PROJECTS) {
                        throw new ProjectException("Too many legacy Project Go projects to inspect safely");
                    }
                    for (Path file : projectFiles) {
                        long bytes = Files.size(file);
                        if (bytes > MAX_LEGACY_PROJECT_BYTES) {
                            throw new ProjectException("Legacy project is too large to inspect safely: " + file);
                        }
                        String before = fileDigest(file);
                        LocalizationProject candidate = projects.read(file);
                        String after = fileDigest(file);
                        if (!before.equals(after)) {
                            throw new ProjectException("Legacy project changed while it was inspected: " + file);
                        }
                        if (candidate.sourceModId().equals(modId)) {
                            int translated = (int) candidate.entries().stream()
                                    .filter(entry -> !entry.translatedText().isBlank()).count();
                            candidates.add(new LegacyProjectCandidate(file.toRealPath(), before,
                                    bytes, candidate.sourceModId(), candidate.patchName(),
                                    candidate.patchId(), candidate.entries().size(), translated,
                                    Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS)
                                            .toInstant()));
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not discover existing translation projects", exception);
        }
        return candidates.stream().sorted(Comparator.comparing(candidate ->
                candidate.file().toString())).toList();
    }

    private static Path legacySearchParent(ModInputPreparationService.PreparedMod prepared) {
        return switch (prepared.kind()) {
            case ZIP_ARCHIVE -> prepared.originalInput().getParent();
            case MOD_INFO_FILE, DIRECTORY -> prepared.modRoot().getParent();
        };
    }

    /** Internal, digest-named workspace; the digest never becomes a visible name. */
    private Path workspaceFor(String modId) {
        return root.resolve(digest(modId + "\nen"));
    }

    /** Internal, digest-named workspace for one explicitly separated source. */
    private Path forkWorkspaceFor(String modId, LocalizationProject project) {
        return root.resolve(digest(modId + "\nen\nfork\n" + entryIdDigest(project)));
    }

    /** True when two non-empty projects have no source-text identity in common. */
    private static boolean sharesNoSourceText(LocalizationProject saved, LocalizationProject candidate) {
        if (saved.entries().isEmpty() || candidate.entries().isEmpty()) {
            return false;
        }
        Set<String> identities = new HashSet<>();
        for (ProjectEntry entry : saved.entries()) {
            identities.add(entryIdentity(entry));
        }
        for (ProjectEntry entry : candidate.entries()) {
            if (identities.contains(entryIdentity(entry))) {
                return false;
            }
        }
        return true;
    }

    private static String entryIdentity(ProjectEntry entry) {
        // Structured encoding prevents delimiter collisions in paths, keys or text.
        return JSON.createArrayNode().add(entry.sourceFile().toString().replace('\\', '/'))
                .add(entry.key()).add(entry.originalText()).toString();
    }

    private static boolean sharesEntryLocations(LocalizationProject saved, LocalizationProject candidate) {
        Set<String> locations = new HashSet<>();
        for (ProjectEntry entry : saved.entries()) {
            locations.add(JSON.createArrayNode().add(entry.sourceFile().toString().replace('\\', '/'))
                    .add(entry.key()).toString());
        }
        return candidate.entries().stream().anyMatch(entry -> locations.contains(
                JSON.createArrayNode().add(entry.sourceFile().toString().replace('\\', '/'))
                        .add(entry.key()).toString()));
    }

    private static String entryIdDigest(LocalizationProject project) {
        List<String> identities = project.entries().stream()
                .map(TranslationWorkflow::entryIdentity).sorted().toList();
        return digest(String.join("\n", identities));
    }

    /** Finds a previously separated copy at the exact source the user selected. */
    private Optional<Path> registeredForkByPath(Path primary, Path canonical) {
        String source = canonical.toString();
        for (JsonNode entry : readLineage(primary).path("forks")) {
            if (source.equals(entry.path("sourceRoot").asText(""))) {
                Optional<Path> workspace = registeredWorkspace(entry.path("workspace").asText(""));
                if (workspace.isPresent() && Files.isRegularFile(
                        workspace.get().resolve(PROJECT_FILE), LinkOption.NOFOLLOW_LINKS)) {
                    return workspace;
                }
            }
        }
        return Optional.empty();
    }

    /** Finds a previously separated copy whose source texts match, even after a rename. */
    private Optional<Path> registeredForkByEntries(Path primary, LocalizationProject candidate) {
        String expected = entryIdDigest(candidate);
        for (JsonNode entry : readLineage(primary).path("forks")) {
            if (expected.equals(entry.path("entryIdDigest").asText(""))) {
                Optional<Path> workspace = registeredWorkspace(entry.path("workspace").asText(""));
                if (workspace.isPresent() && matchingSavedFork(workspace.get(), candidate)) {
                    return workspace;
                }
            }
        }
        // Older registries hashed only entry locations. Resolve their saved
        // projects by content instead, preserving rename recovery on upgrade.
        for (JsonNode entry : readLineage(primary).path("forks")) {
            Optional<Path> workspace = registeredWorkspace(entry.path("workspace").asText(""));
            if (workspace.isPresent() && matchingSavedFork(workspace.get(), candidate)) {
                return workspace;
            }
        }
        return Optional.empty();
    }

    private boolean matchingSavedFork(Path workspace, LocalizationProject candidate) {
        if (!Files.isRegularFile(workspace.resolve(PROJECT_FILE), LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            LocalizationProject saved = projects.read(workspace.resolve(PROJECT_FILE));
            return saved.sourceModId().equals(candidate.sourceModId())
                    && entryIdDigest(saved).equals(entryIdDigest(candidate));
        } catch (ProjectException exception) {
            return false;
        }
    }

    private Optional<Path> registeredWorkspace(String name) {
        if (!name.matches("[0-9a-f]{64}")) {
            return Optional.empty();
        }
        Path workspace = root.resolve(name).normalize();
        return root.equals(workspace.getParent()) && !Files.isSymbolicLink(workspace)
                ? Optional.of(workspace)
                : Optional.empty();
    }

    /**
     * Records hidden source-lineage evidence beside the primary workspace so a
     * later load can tell genuinely different sources that share one mod id apart.
     * The recorded digests and paths stay internal and are never shown to users.
     * This evidence is advisory: failing to record it must never fail a load.
     */
    private void recordLineage(Path primary, SourceModIdentity identity, Path canonical,
            Path workspace, LocalizationProject project, boolean separated) {
        ObjectNode lineage = readLineage(primary);
        lineage.put("schemaVersion", LINEAGE_SCHEMA_VERSION);
        lineage.put("sourceModId", identity.originalId());
        if (!separated) {
            lineage.put("modName", identity.originalName());
            lineage.put("gameVersion", identity.gameVersion());
            lineage.put("folderName", identity.originalFolderName());
            lineage.put("sourceRoot", canonical.toString());
            lineage.put("entryIdDigest", entryIdDigest(project));
            writeLineage(primary, lineage);
            return;
        }
        var forks = lineage.withArray("forks");
        String name = java.util.Objects.requireNonNull(workspace.getFileName(), "workspace").toString();
        ObjectNode registered = null;
        for (JsonNode existing : forks) {
            if (existing.isObject() && name.equals(existing.path("workspace").asText(""))) {
                registered = (ObjectNode) existing;
            }
        }
        if (registered == null) {
            if (forks.size() >= MAX_REGISTERED_FORKS) {
                LOG.log(System.Logger.Level.WARNING, "Too many separated copies to register");
                return;
            }
            registered = forks.addObject();
            registered.put("workspace", name);
            registered.put("registeredAt", java.time.Instant.now().toString());
        }
        registered.put("sourceRoot", canonical.toString());
        registered.put("entryIdDigest", entryIdDigest(project));
        registered.put("modName", identity.originalName());
        registered.put("gameVersion", identity.gameVersion());
        writeLineage(primary, lineage);
    }

    private ObjectNode readLineage(Path primary) {
        Path file = primary.resolve(LINEAGE_FILE);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return JSON.createObjectNode();
        }
        try {
            if (Files.size(file) > MAX_LINEAGE_BYTES) {
                return JSON.createObjectNode();
            }
            JsonNode lineage = JSON.readTree(file.toFile());
            if (!(lineage instanceof ObjectNode)
                    || lineage.path("schemaVersion").asInt(-1) != LINEAGE_SCHEMA_VERSION
                    || !workspaceFor(lineage.path("sourceModId").asText("")).equals(primary)
                    || (lineage.has("forks") && (!lineage.path("forks").isArray()
                            || lineage.path("forks").size() > MAX_REGISTERED_FORKS))) {
                return JSON.createObjectNode();
            }
            return ((ObjectNode) lineage).deepCopy();
        } catch (IOException exception) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable source lineage record", exception);
            return JSON.createObjectNode();
        }
    }

    private void writeLineage(Path primary, ObjectNode lineage) {
        Path staging = null;
        try {
            Files.createDirectories(primary);
            staging = Files.createTempFile(primary, ".lineage-", ".json");
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), lineage);
            publisher.publish(staging, primary.resolve(LINEAGE_FILE));
        } catch (IOException exception) {
            LOG.log(System.Logger.Level.WARNING, "Could not record source lineage", exception);
        } finally {
            if (staging != null) {
                try { Files.deleteIfExists(staging); }
                catch (IOException exception) {
                    LOG.log(System.Logger.Level.WARNING, "Lineage staging cleanup failed", exception);
                }
            }
        }
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
            SourceModIdentity identity, ObjectNode metadata) throws ProjectException {
        Path staging = null;
        try {
            staging = Files.createTempFile(workspace, ".candidate-", ".json");
            projects.write(staging, candidate);
            ObjectNode document = (ObjectNode) JSON.readTree(staging.toFile());
            metadata.put("workspaceVersion", 1);
            metadata.put("sourceRoot", source.toString());
            // Source metadata is recorded exactly as declared; it is never overwritten.
            metadata.put("modName", identity.originalName());
            metadata.put("sourceModId", identity.originalId());
            metadata.put("sourceFolderName", identity.originalFolderName());
            metadata.put("sourceGameVersion", identity.gameVersion());
            metadata.put("sourceLanguage", new SourceLanguageDetector().detect(candidate.entries()));
            // Embed metadata with the project: a single publication commits both together.
            document.set("workspace", metadata);
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), document);
            String committedRevision = revision(staging);
            Path target = workspace.resolve(PROJECT_FILE);
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE)) { channel.force(true); }
            publisher.publish(staging, target);
            return new Session(source, workspace, candidate, identity,
                    PresentationNames.forMod(identity, TARGET_LANGUAGE),
                    metadata.path("sourceLanguage").asText(), committedRevision,
                    metadata.path("pendingReview").size());
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

    private static String fileDigest(Path file) throws ProjectException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ProjectException("Could not hash legacy translation project", exception);
        }
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
