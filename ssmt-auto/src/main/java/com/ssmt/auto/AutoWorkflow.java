package com.ssmt.auto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.ModInfo;
import com.ssmt.project.AiTranslationExchangeService;
import com.ssmt.project.AiTranslationImportResult;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ModInputPreparationService;
import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectEntry;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.SourceLanguageDetector;
import com.ssmt.scanner.ModInfoReader;
import com.ssmt.tm.MasterTranslationLibrary;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Source-safe state machine that reuses and grows one master translation library.
 */
public final class AutoWorkflow {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int STATE_VERSION = 1;
    private static final String CATALOG_FILE = MasterTranslationLibrary.DEFAULT_FILENAME;
    private static final int MAX_RESPONSE_CANDIDATES = 128;
    private static final long MAX_DISCOVERED_RESPONSE_BYTES = 16L * 1024 * 1024;

    private final LocalizationProjectService projects =
            new LocalizationProjectService();
    private final AiTranslationExchangeService exchange =
            new AiTranslationExchangeService();
    private final SourceLanguageDetector languages = new SourceLanguageDetector();
    private final ModInfoReader modInfoReader = new ModInfoReader();
    private final ModInputPreparationService inputs = new ModInputPreparationService();
    private final Path sharedCatalog;
    private final Path workspaceRoot;

    /**
     * Creates a workflow using the persistent catalog shared by all auto projects.
     */
    public AutoWorkflow() {
        this(defaultSharedCatalog(), defaultWorkspaceRoot());
    }

    AutoWorkflow(Path sharedCatalog) {
        this(sharedCatalog, sharedCatalog.resolveSibling("projects"));
    }

    /**
     * Creates a workflow with explicit internal storage locations.
     *
     * @param sharedCatalog persistent catalog shared by auto projects
     * @param workspaceRoot internal root for per-source project workspaces
     */
    public AutoWorkflow(Path sharedCatalog, Path workspaceRoot) {
        this.sharedCatalog = sharedCatalog.toAbsolutePath().normalize();
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    /**
     * Runs the drop-friendly workflow for a mod directory, its metadata file,
     * or a ZIP archive containing one mod.
     *
     * @param dropped item supplied by the operating system
     * @return result and next action
     * @throws ProjectException when the dropped item is not a safe mod input
     */
    public AutoRunResult runDropped(Path dropped) throws ProjectException {
        Path supplied = dropped.toAbsolutePath().normalize();
        boolean archive = Files.isRegularFile(supplied)
                && fileName(supplied).toLowerCase(Locale.ROOT).endsWith(".zip");
        if (!archive) {
            var prepared = inputs.prepare(supplied, workspaceRoot.resolve("input-cache"));
            return run(prepared.modRoot());
        }
        Path workspace = workspaceFor(
                supplied,
                safeName(withoutExtension(fileName(supplied)), "Mod archive"));
        Path visibleRoot = Objects.requireNonNull(supplied.getParent(), "archive parent");
        var prepared = inputs.prepare(supplied, workspace);
        return run(prepared.modRoot(), workspace, visibleRoot);
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
        ModInfo mod = readMod(source);
        Path visibleRoot = Objects.requireNonNull(source.getParent(), "source mod parent");
        return run(
                source,
                workspaceFor(source, safeName(mod.name(), mod.id())),
                visibleRoot);
    }

    private AutoRunResult run(Path sourceRoot, Path workspace, Path visibleRoot)
            throws ProjectException {
        Path source = sourceRoot.toAbsolutePath().normalize();
        ModInfo mod = readMod(source);
        workspace = workspace.toAbsolutePath().normalize();
        visibleRoot = visibleRoot.toAbsolutePath().normalize();
        String originalName = safeName(mod.name(), mod.id());
        Path stateFile = workspace.resolve("project-go-state.json");
        Path legacyCatalog = workspace.resolve(CATALOG_FILE);
        Path missing = visibleRoot.resolve(originalName + " - AI translation request.json");
        Path translated = visibleRoot.resolve(originalName + " - AI translation library.json");
        Path patch = visibleRoot.resolve(safeName(mod.id(), "translation") + ".english");
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
        Path response = findResponse(
                visibleRoot, translated, missing, project, sourceLanguage, responseHash);
        if (response != null) {
            String currentHash = sha256(response);
            if (!currentHash.equals(responseHash)) {
                AiTranslationImportResult imported =
                        exchange.importResponse(response, project, sharedCatalog);
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
                    Files.isRegularFile(sharedCatalog)
                            ? AutoRunResult.Status.MASTER_LIBRARY_INCOMPLETE
                            : AutoRunResult.Status.MASTER_LIBRARY_NEEDED,
                    workspace,
                    "Send " + fileName(missing)
                            + " to an AI. Save its validated JSON response as "
                            + fileName(translated) + " (or any JSON filename beside the mod)"
                            + ", then drop the same mod again. The response is imported into "
                            + "your master translation library. Master library: "
                            + sharedCatalog);
        }

        ProjectBuildResult build = projects.buildTranslatedCopy(source, patch, project);
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
                "Translated copy: " + patch + "; master library: " + sharedCatalog);
    }

    private ModInfo readMod(Path source) throws ProjectException {
        try {
            return modInfoReader.read(source);
        } catch (com.ssmt.core.exception.SsmtParseException exception) {
            throw new ProjectException("Could not read dropped mod_info.json", exception);
        }
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
        return MasterTranslationLibrary.currentUserDefault();
    }

    private static Path defaultWorkspaceRoot() {
        Path applicationData = MasterTranslationLibrary.resolve(
                Optional.empty(),
                Optional.ofNullable(System.getenv("LOCALAPPDATA")),
                Path.of(System.getProperty("user.home")));
        return Objects.requireNonNull(applicationData.getParent(), "application data")
                .resolve("projects");
    }

    private Path workspaceFor(Path source, String displayName) throws ProjectException {
        String identity = source.toAbsolutePath().normalize().toString();
        if (isWindows()) {
            identity = identity.toLowerCase(Locale.ROOT);
        }
        String key = sha256(identity);
        return workspaceRoot.resolve(displayName + "-" + key).normalize();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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

    private static Path findResponse(
            Path visibleRoot,
            Path documentedResponse,
            Path request,
            LocalizationProject project,
            String sourceLanguage,
            String importedHash) throws ProjectException {
        if (Files.isRegularFile(documentedResponse)
                && !sha256(documentedResponse).equals(importedHash)) {
            if (isMatchingResponse(documentedResponse, project.sourceModId(), sourceLanguage,
                    expectedSources(project))) {
                return documentedResponse;
            }
            throw new ProjectException(
                    "The documented AI response does not match the current English translation request");
        }
        Map<String, String> expectedSources = expectedSources(project);
        List<Path> candidates = new ArrayList<>();
        try (var files = Files.newDirectoryStream(
                visibleRoot,
                path -> Files.isRegularFile(path)
                        && fileName(path).toLowerCase(Locale.ROOT).endsWith(".json"))) {
            int inspected = 0;
            for (Path candidate : files) {
                inspected++;
                if (inspected > MAX_RESPONSE_CANDIDATES) {
                    throw new ProjectException(
                            "Too many sibling JSON files to safely find an AI response; "
                                    + "use the documented response filename");
                }
                Path normalized = candidate.toAbsolutePath().normalize();
                if (normalized.equals(request) || normalized.equals(documentedResponse)) {
                    continue;
                }
                if (Files.size(normalized) > MAX_DISCOVERED_RESPONSE_BYTES) {
                    continue;
                }
                if (isMatchingResponse(normalized, project.sourceModId(), sourceLanguage, expectedSources)
                        && !sha256(normalized).equals(importedHash)) {
                    candidates.add(normalized);
                }
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not search for an AI translation response", exception);
        }
        candidates.sort(Comparator.comparing(AutoWorkflow::fileName));
        if (candidates.size() > 1) {
            throw new ProjectException(
                    "More than one new AI response matches this mod; keep one beside the mod "
                            + "or use the documented response filename");
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private static boolean isMatchingResponse(
            Path candidate,
            String sourceModId,
            String sourceLanguage,
            Map<String, String> expectedSources) {
        try {
            JsonNode root = JSON.readTree(candidate.toFile());
            if (root == null
                    || root.path("schemaVersion").asInt(-1) != 1
                    || !sourceModId.equals(root.path("sourceModId").asText())
                    || !sourceLanguage.equals(root.path("sourceLanguage").asText())
                    || !"en".equals(root.path("targetLanguage").asText())
                    || !root.path("entries").isArray()
                    || root.path("entries").isEmpty()) {
                return false;
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode item : root.path("entries")) {
                String id = item.path("id").asText("");
                String expectedSource = expectedSources.get(id);
                if (id.isBlank()
                        || expectedSource == null
                        || !expectedSource.equals(item.path("source").asText())
                        || item.path("translation").asText("").isBlank()) {
                    return false;
                }
                ids.add(id);
            }
            return root.path("entryCount").asInt(-1) == ids.size()
                    && root.path("entryIdsSha256").asText().equals(identityDigest(ids));
        } catch (IOException | ProjectException exception) {
            return false;
        }
    }

    private static Map<String, String> expectedSources(LocalizationProject project) {
        Map<String, String> expected = new HashMap<>();
        for (ProjectEntry entry : project.entries()) {
            expected.put(entry.sourceFile().toString().replace('\\', '/') + "#" + entry.key(),
                    entry.originalText());
        }
        return expected;
    }

    private static String identityDigest(List<String> identities) throws ProjectException {
        String value = identities.stream().sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        return sha256(value);
    }

    private static String fileName(Path path) {
        return Objects.requireNonNull(path.getFileName(), "filename").toString();
    }

    private static String withoutExtension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator > 0 ? fileName.substring(0, separator) : fileName;
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

    private static String sha256(String value) throws ProjectException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ProjectException("Could not fingerprint source identity", exception);
        }
    }

    private record State(
            int schemaVersion,
            String modVersion,
            String projectFile,
            String responseHash) {
    }
}
