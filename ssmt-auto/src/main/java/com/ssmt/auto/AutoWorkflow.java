package com.ssmt.auto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.ModInfo;
import com.ssmt.project.AiTranslationExchangeService;
import com.ssmt.project.AiTranslationImportResult;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectEntry;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.SourceLanguageDetector;
import com.ssmt.scanner.ModInfoReader;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Source-safe state machine used by the drag-and-drop automation executable.
 */
public final class AutoWorkflow {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int STATE_VERSION = 1;
    private static final String CATALOG_FILE = "project-go-catalog.db";

    private final LocalizationProjectService projects =
            new LocalizationProjectService();
    private final AiTranslationExchangeService exchange =
            new AiTranslationExchangeService();
    private final SourceLanguageDetector languages = new SourceLanguageDetector();
    private final ModInfoReader modInfoReader = new ModInfoReader();
    private final Path sharedCatalog;

    /**
     * Creates a workflow using the persistent catalog shared by all auto projects.
     */
    public AutoWorkflow() {
        this(defaultSharedCatalog());
    }

    AutoWorkflow(Path sharedCatalog) {
        this.sharedCatalog = sharedCatalog.toAbsolutePath().normalize();
    }

    /**
     * Executes one deterministic automation pass.
     *
     * @param sourceRoot source mod directory
     * @return result and next action
     * @throws ProjectException when the workflow cannot safely continue
     */
    public AutoRunResult run(Path sourceRoot) throws ProjectException {
        Path source = sourceRoot.toAbsolutePath().normalize();
        ModInfo mod;
        try {
            mod = modInfoReader.read(source);
        } catch (com.ssmt.core.exception.SsmtParseException exception) {
            throw new ProjectException("Could not read dropped mod_info.json", exception);
        }
        Path parent = Objects.requireNonNull(source.getParent(), "source mod parent");
        String originalName = safeName(mod.name(), mod.id());
        Path workspace = parent.resolve("Project Go - " + originalName);
        Path stateFile = workspace.resolve("project-go-state.json");
        Path legacyCatalog = workspace.resolve(CATALOG_FILE);
        Path missing = workspace.resolve(originalName + " - words-to-translate.json");
        Path translated = workspace.resolve(originalName + " - words-translated.json");
        Path patch = workspace.resolve(safeName(mod.id(), "translation") + ".english");
        try {
            Files.createDirectories(workspace);
            prepareSharedCatalog(legacyCatalog);
        } catch (IOException exception) {
            throw new ProjectException(
                    "Could not prepare automation workspace or shared catalog",
                    exception);
        }

        State state = readState(stateFile);
        Path projectFile = state == null
                ? workspace.resolve("Translation - " + originalName + ".ssmt.json")
                : workspace.resolve(state.projectFile()).normalize();
        if (!projectFile.startsWith(workspace)) {
            throw new ProjectException("Automation state contains an unsafe project path");
        }
        LocalizationProject project;
        String currentVersion = Objects.requireNonNullElse(mod.version(), "");
        if (Files.isRegularFile(projectFile)) {
            project = projects.read(projectFile);
            if (state == null || !currentVersion.equals(state.modVersion())) {
                ProjectRefreshResult refresh = projects.refresh(source, project);
                project = refresh.project();
                projects.write(projectFile, project);
            }
        } else {
            project = projects.create(
                    source,
                    safeName(mod.id(), "translation") + ".english",
                    "Translation (" + mod.name() + ")");
            projects.write(projectFile, project);
        }

        String sourceLanguage = languages.detect(project.entries());
        project = applyUniqueExactMatches(
                project, sharedCatalog, sourceLanguage, "en");
        projects.write(projectFile, project);

        String responseHash = state == null ? "" : state.responseHash();
        if (Files.isRegularFile(translated)) {
            String currentHash = sha256(translated);
            if (!currentHash.equals(responseHash)) {
                AiTranslationImportResult imported =
                        exchange.importResponse(translated, project, sharedCatalog);
                project = imported.project();
                responseHash = currentHash;
                Path suggested = workspace.resolve(projectFileName(
                        project.patchName(), mod.name()));
                projects.write(suggested, project);
                projectFile = suggested;
                project = applyUniqueExactMatches(
                        project, sharedCatalog, sourceLanguage, "en");
                projects.write(projectFile, project);
            }
        }

        List<ProjectEntry> untranslated = project.entries().stream()
                .filter(entry -> !entry.originalText().isBlank())
                .filter(entry -> entry.translatedText().isBlank())
                .toList();
        if (!untranslated.isEmpty()) {
            exchange.exportPackage(
                    missing,
                    project,
                    untranslated,
                    mod.name(),
                    sourceLanguage,
                    "en");
            writeState(
                    stateFile,
                    new State(
                            STATE_VERSION,
                            currentVersion,
                            fileName(projectFile),
                            responseHash));
            return new AutoRunResult(
                    AutoRunResult.Status.WAITING_FOR_TRANSLATION,
                    workspace,
                    "Translate " + fileName(missing)
                            + ", save the response as " + fileName(translated)
                            + ", then drop mod_info.json again. Shared catalog: "
                            + sharedCatalog);
        }

        ProjectBuildResult build = projects.build(source, patch, project);
        writeState(
                stateFile,
                new State(
                        STATE_VERSION,
                        currentVersion,
                        fileName(projectFile),
                        responseHash));
        return new AutoRunResult(
                build.changed()
                        ? AutoRunResult.Status.PATCH_PUBLISHED
                        : AutoRunResult.Status.PATCH_UNCHANGED,
                workspace,
                "Translated clone: " + patch + "; pristine source backup: "
                        + patch + "-source-backup; shared catalog: " + sharedCatalog);
    }

    private void prepareSharedCatalog(Path legacyCatalog) throws IOException {
        Path parent = sharedCatalog.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(sharedCatalog) && Files.isRegularFile(legacyCatalog)) {
            Files.copy(legacyCatalog, sharedCatalog);
        }
    }

    private static Path defaultSharedCatalog() {
        String configured = System.getProperty("ssmt.catalog", "").strip();
        if (configured.isEmpty()) {
            configured = System.getenv().getOrDefault(
                    "SSMT_TRANSLATION_MEMORY", "").strip();
        }
        if (!configured.isEmpty()) {
            return Path.of(configured);
        }
        String localAppData = System.getenv().getOrDefault(
                "LOCALAPPDATA", "").strip();
        Path dataRoot = localAppData.isEmpty()
                ? Path.of(System.getProperty("user.home"), ".ssmt")
                : Path.of(localAppData, "Project Go");
        return dataRoot.resolve(CATALOG_FILE);
    }

    private static LocalizationProject applyUniqueExactMatches(
            LocalizationProject project,
            Path catalog,
            String sourceLanguage,
            String targetLanguage) throws ProjectException {
        if (!Files.isRegularFile(catalog)) {
            return project;
        }
        List<ProjectEntry> entries = new ArrayList<>();
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            for (ProjectEntry entry : project.entries()) {
                if (!entry.translatedText().isBlank() || entry.originalText().isBlank()) {
                    entries.add(entry);
                    continue;
                }
                List<String> matches = memory.findExactTranslations(
                        entry.originalText(), sourceLanguage, targetLanguage);
                entries.add(matches.size() == 1
                        ? entry.withTranslatedText(matches.getFirst())
                        : entry);
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException("Could not reuse translation catalog", exception);
        }
        return project.withEntries(entries);
    }

    private static State readState(Path stateFile) throws ProjectException {
        if (!Files.isRegularFile(stateFile)) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(stateFile.toFile());
            int version = root.path("schemaVersion").asInt(-1);
            if (version != STATE_VERSION) {
                throw new ProjectException(
                        "Unsupported automation state version " + version);
            }
            return new State(
                    version,
                    root.path("modVersion").asText(),
                    root.path("projectFile").asText(),
                    root.path("responseHash").asText());
        } catch (IOException exception) {
            throw new ProjectException("Could not read automation state", exception);
        }
    }

    private static void writeState(Path stateFile, State state)
            throws ProjectException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", state.schemaVersion());
        root.put("modVersion", state.modVersion());
        root.put("projectFile", state.projectFile());
        root.put("responseHash", state.responseHash());
        Path staged = stateFile.resolveSibling(
                stateFile.getFileName() + ".ssmt-stage");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), root);
            try {
                Files.move(
                        staged,
                        stateFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staged, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not write automation state", exception);
        } finally {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException ignored) {
                // Failed cleanup does not invalidate a published state file.
            }
        }
    }

    private static String projectFileName(String patchName, String originalName) {
        String suffix = " (" + originalName + ")";
        String translatedName = patchName.endsWith(suffix)
                ? patchName.substring(0, patchName.length() - suffix.length())
                : patchName;
        return safeName(translatedName, "Translation")
                + " - " + safeName(originalName, "Mod") + ".ssmt.json";
    }

    private static String fileName(Path path) {
        return Objects.requireNonNull(path.getFileName(), "filename").toString();
    }

    private static String safeName(String value, String fallback) {
        String safe = Objects.requireNonNullElse(value, "")
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "-")
                .strip()
                .replaceAll("[. ]+$", "");
        if (safe.isBlank()) {
            safe = fallback;
        }
        return safe.length() <= 80 ? safe : safe.substring(0, 80).strip();
    }

    private static String sha256(Path file) throws ProjectException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ProjectException("Could not fingerprint translated response", exception);
        }
    }

    private record State(
            int schemaVersion,
            String modVersion,
            String projectFile,
            String responseHash) {
    }
}
