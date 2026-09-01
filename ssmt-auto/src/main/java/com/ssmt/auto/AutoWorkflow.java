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
import com.ssmt.tm.MasterTranslationLibrary;
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
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Source-safe state machine that reuses and grows one master translation library.
 */
public final class AutoWorkflow {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int STATE_VERSION = 1;
    private static final String CATALOG_FILE = MasterTranslationLibrary.DEFAULT_FILENAME;
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_ARCHIVE_BYTES = 1_073_741_824L;

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
     * Runs the drop-friendly workflow for a mod directory, its metadata file,
     * or a ZIP archive containing one mod.
     *
     * @param dropped item supplied by the operating system
     * @return result and next action
     * @throws ProjectException when the dropped item is not a safe mod input
     */
    public AutoRunResult runDropped(Path dropped) throws ProjectException {
        Path supplied = dropped.toAbsolutePath().normalize();
        if (Files.isDirectory(supplied)) {
            return run(supplied);
        }
        if (!Files.isRegularFile(supplied)) {
            throw new ProjectException("The dropped item is not a file or folder: " + supplied);
        }
        if ("mod_info.json".equalsIgnoreCase(fileName(supplied))) {
            Path parent = supplied.getParent();
            if (parent == null) {
                throw new ProjectException("The dropped mod_info.json has no mod folder");
            }
            return run(parent);
        }
        if (!fileName(supplied).toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ProjectException("Drop a ZIP mod archive, mod_info.json, or mod folder");
        }
        Path parent = Objects.requireNonNull(supplied.getParent(), "archive parent");
        Path workspace = parent.resolve("Project Go - " + safeName(
                withoutExtension(fileName(supplied)), "Mod archive"));
        try {
            Files.createDirectories(workspace);
            return run(extractArchive(supplied, workspace), workspace);
        } catch (IOException exception) {
            throw new ProjectException("Could not unpack dropped mod archive", exception);
        }
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
        Path parent = Objects.requireNonNull(source.getParent(), "source mod parent");
        ModInfo mod = readMod(source);
        return run(source, parent.resolve("Project Go - " + safeName(mod.name(), mod.id())));
    }

    private AutoRunResult run(Path sourceRoot, Path workspace) throws ProjectException {
        Path source = sourceRoot.toAbsolutePath().normalize();
        ModInfo mod = readMod(source);
        workspace = workspace.toAbsolutePath().normalize();
        String originalName = safeName(mod.name(), mod.id());
        Path stateFile = workspace.resolve("project-go-state.json");
        Path legacyCatalog = workspace.resolve(CATALOG_FILE);
        Path missing = workspace.resolve(originalName + " - AI translation request.json");
        Path translated = workspace.resolve(originalName + " - AI translation library.json");
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
                    Files.isRegularFile(sharedCatalog)
                            ? AutoRunResult.Status.MASTER_LIBRARY_INCOMPLETE
                            : AutoRunResult.Status.MASTER_LIBRARY_NEEDED,
                    workspace,
                    "Send " + fileName(missing)
                            + " to an AI. Save its validated JSON response as "
                            + fileName(translated)
                            + ", then drop the same mod again. The response is imported into "
                            + "your master translation library. Master library: "
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
                        + patch + "-source-backup; master library: " + sharedCatalog);
    }

    private ModInfo readMod(Path source) throws ProjectException {
        try {
            return modInfoReader.read(source);
        } catch (com.ssmt.core.exception.SsmtParseException exception) {
            throw new ProjectException("Could not read dropped mod_info.json", exception);
        }
    }

    private static Path extractArchive(Path archive, Path workspace)
            throws IOException, ProjectException {
        String archiveHash = sha256(archive);
        Path extraction = workspace.resolve("archive-source-" + archiveHash.substring(0, 12));
        if (Files.isDirectory(extraction)) {
            return findArchiveModRoot(extraction);
        }
        Files.createDirectories(extraction);
        int entries = 0;
        long extractedBytes = 0;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new ProjectException("Mod archive contains too many files");
                }
                Path destination = extraction.resolve(entry.getName()).normalize();
                if (!destination.startsWith(extraction)) {
                    throw new ProjectException("Mod archive contains an unsafe file path");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                try (var output = Files.newOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        extractedBytes += read;
                        if (extractedBytes > MAX_ARCHIVE_BYTES) {
                            throw new ProjectException("Mod archive expands beyond the 1 GB safety limit");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
        return findArchiveModRoot(extraction);
    }

    private static Path findArchiveModRoot(Path extraction) throws IOException, ProjectException {
        try (var paths = Files.walk(extraction)) {
            List<Path> metadata = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> "mod_info.json".equalsIgnoreCase(fileName(path)))
                    .toList();
            if (metadata.size() != 1) {
                throw new ProjectException(
                        "Mod archive must contain exactly one mod_info.json file");
            }
            return Objects.requireNonNull(metadata.getFirst().getParent(), "mod archive root");
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

    private record State(
            int schemaVersion,
            String modVersion,
            String projectFile,
            String responseHash) {
    }
}
