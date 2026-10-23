package com.ssmt.project;

import static com.ssmt.project.TranslationWorkflow.digest;
import static com.ssmt.project.TranslationWorkflow.entryIdDigest;
import static com.ssmt.project.TranslationWorkflow.PROJECT_FILE;
import com.ssmt.project.TranslationWorkflow.WorkspacePublisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/** Bounded registry for resuming separate sources that share a mod id. */
final class WorkflowLineageStore {
    private static final String LINEAGE_FILE = "lineage.json";
    private static final int LINEAGE_SCHEMA_VERSION = 1;
    private static final int MAX_REGISTERED_FORKS = 32;
    private static final long MAX_LINEAGE_BYTES = 1024L * 1024L;
    private final Path root;
    private final LocalizationProjectService projects;
    private final WorkspacePublisher publisher;
    private final ObjectMapper JSON;
    private final System.Logger LOG;
    WorkflowLineageStore(Path root, LocalizationProjectService projects,
            WorkspacePublisher publisher, ObjectMapper json, System.Logger log) {
        this.root = root;
        this.projects = projects;
        this.publisher = publisher;
        this.JSON = json;
        this.LOG = log;
    }

    Path workspaceFor(String modId) {
        return root.resolve(digest(modId + "\nen"));
    }

    Optional<Path> registeredForkByPath(Path primary, Path canonical) {
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

    Optional<Path> registeredForkByEntries(Path primary, LocalizationProject candidate) {
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

    boolean matchingSavedFork(Path workspace, LocalizationProject candidate) {
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

    Optional<Path> registeredWorkspace(String name) {
        if (!name.matches("[0-9a-f]{64}")) {
            return Optional.empty();
        }
        Path workspace = root.resolve(name).normalize();
        return root.equals(workspace.getParent()) && !Files.isSymbolicLink(workspace)
                ? Optional.of(workspace)
                : Optional.empty();
    }

    void recordLineage(Path primary, SourceModIdentity identity, Path canonical,
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

    ObjectNode readLineage(Path primary) {
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

    void writeLineage(Path primary, ObjectNode lineage) {
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
}
