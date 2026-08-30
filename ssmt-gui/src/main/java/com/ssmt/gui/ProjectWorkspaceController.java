package com.ssmt.gui;

import com.ssmt.core.CancellationToken;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.project.AiTranslationExchangeService;
import com.ssmt.project.AiTranslationImportResult;
import com.ssmt.project.AiImportPolicy;
import com.ssmt.project.BrowserAiReviewExport;
import com.ssmt.project.BrowserAiReviewService;
import com.ssmt.project.FontCoverageAuditor;
import com.ssmt.project.FontCoverageFinding;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRecoveryService;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.ProjectSourceDetails;
import com.ssmt.project.SourceLanguageDetector;
import com.ssmt.project.TranslationCoverageAuditor;
import com.ssmt.project.TranslationCoverageReport;
import com.ssmt.project.ProjectEntryTranslationEngine;
import com.ssmt.project.ProjectTranslationCoordinator;
import com.ssmt.project.ProjectTranslationResult;
import com.ssmt.project.ProjectTranslationSettings;
import com.ssmt.project.ProjectTranslationCheckpointService;
import com.ssmt.project.TranslationMetadataInterchangeService;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import com.ssmt.tm.TranslationMemoryMergeResult;
import com.ssmt.tm.TranslationMemoryMergeService;
import com.ssmt.validation.font.BmFontGlyphSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Toolkit-independent state transitions for the desktop project workspace.
 */
public final class ProjectWorkspaceController {
    private final ProjectWorkflow workflow;
    private final TranslationEditorController editor;
    private final ProjectRecoveryService recovery = new ProjectRecoveryService();
    private final LocalizationProjectService projectService =
            new LocalizationProjectService();
    private final AiTranslationExchangeService aiExchange =
            new AiTranslationExchangeService();
    private final BrowserAiReviewService browserReview = new BrowserAiReviewService();
    private final ProjectTranslationCheckpointService translationCheckpoints =
            new ProjectTranslationCheckpointService();
    private final SourceLanguageDetector languageDetector =
            new SourceLanguageDetector();
    private final TranslationMemoryMergeService memoryMerge =
            new TranslationMemoryMergeService();
    private LocalizationProject project;
    private Path projectFile;
    private Path sourceRoot;
    private Path outputRoot;
    private Path jsonSchemaCatalog;
    private Path csvSchemaCatalog;

    public ProjectWorkspaceController(TranslationEditorController editor) {
        this(new ServiceWorkflow(new LocalizationProjectService()), editor);
    }

    ProjectWorkspaceController(ProjectWorkflow workflow, TranslationEditorController editor) {
        if (workflow == null || editor == null) {
            throw new IllegalArgumentException("Workspace services must not be null");
        }
        this.workflow = workflow;
        this.editor = editor;
    }

    /**
     * Extracts and writes a new project, then loads it into the editor.
     *
     * @param source selected source mod
     * @param destination project JSON destination
     * @param patchId patch mod id
     * @param patchName patch display name
     * @throws ProjectException on extraction or write failure
     */
    public void create(Path source, Path destination, String patchId, String patchName)
            throws ProjectException {
        create(
                source,
                destination,
                patchId,
                patchName,
                Optional.empty(),
                Optional.empty(),
                CancellationToken.NONE);
    }

    /**
     * Creates a project with cooperative cancellation.
     *
     * @param source selected source mod
     * @param destination project JSON destination
     * @param patchId patch mod id
     * @param patchName patch display name
     * @param cancellation cancellation signal
     * @throws ProjectException on extraction or write failure
     */
    public void create(
            Path source,
            Path destination,
            String patchId,
            String patchName,
            CancellationToken cancellation) throws ProjectException {
        create(
                source,
                destination,
                patchId,
                patchName,
                Optional.empty(),
                Optional.empty(),
                cancellation);
    }

    /**
     * Extracts a new project with an opt-in JSON schema.
     *
     * @param source selected source mod
     * @param destination project JSON destination
     * @param patchId patch mod id
     * @param patchName patch display name
     * @param jsonSchemaCatalog custom JSON schema catalog
     * @throws ProjectException on schema, extraction, or write failure
     */
    public void createWithJsonSchema(
            Path source,
            Path destination,
            String patchId,
            String patchName,
            Path jsonSchemaCatalog) throws ProjectException {
        createWithJsonSchema(
                source,
                destination,
                patchId,
                patchName,
                jsonSchemaCatalog,
                CancellationToken.NONE);
    }

    /**
     * Creates a JSON-schema-configured project with cooperative cancellation.
     *
     * @param source selected source mod
     * @param destination project JSON destination
     * @param patchId patch mod id
     * @param patchName patch display name
     * @param jsonSchemaCatalog custom JSON schema catalog
     * @param cancellation cancellation signal
     * @throws ProjectException on schema, extraction, or write failure
     */
    public void createWithJsonSchema(
            Path source,
            Path destination,
            String patchId,
            String patchName,
            Path jsonSchemaCatalog,
            CancellationToken cancellation) throws ProjectException {
        create(
                source,
                destination,
                patchId,
                patchName,
                Optional.of(jsonSchemaCatalog),
                Optional.empty(),
                cancellation);
    }

    /**
     * Extracts a new project with an opt-in CSV schema.
     *
     * @param source selected source mod
     * @param destination project JSON destination
     * @param patchId patch mod id
     * @param patchName patch display name
     * @param csvSchemaCatalog custom CSV schema catalog
     * @throws ProjectException on schema, extraction, or write failure
     */
    public void createWithCsvSchema(
            Path source,
            Path destination,
            String patchId,
            String patchName,
            Path csvSchemaCatalog) throws ProjectException {
        createWithCsvSchema(
                source,
                destination,
                patchId,
                patchName,
                csvSchemaCatalog,
                CancellationToken.NONE);
    }

    /**
     * Creates a CSV-schema-configured project with cooperative cancellation.
     *
     * @param source selected source mod
     * @param destination project JSON destination
     * @param patchId patch mod id
     * @param patchName patch display name
     * @param csvSchemaCatalog custom CSV schema catalog
     * @param cancellation cancellation signal
     * @throws ProjectException on schema, extraction, or write failure
     */
    public void createWithCsvSchema(
            Path source,
            Path destination,
            String patchId,
            String patchName,
            Path csvSchemaCatalog,
            CancellationToken cancellation) throws ProjectException {
        create(
                source,
                destination,
                patchId,
                patchName,
                Optional.empty(),
                Optional.of(csvSchemaCatalog),
                cancellation);
    }

    private void create(
            Path source,
            Path destination,
            String patchId,
            String patchName,
            Optional<Path> jsonSchemaCatalog,
            Optional<Path> csvSchemaCatalog,
            CancellationToken cancellation) throws ProjectException {
        LocalizationProject created =
                workflow.create(
                        source, patchId, patchName,
                        jsonSchemaCatalog, csvSchemaCatalog, cancellation);
        workflow.write(destination, created);
        setWorkspace(source, destination, created);
        this.jsonSchemaCatalog = jsonSchemaCatalog
                .map(path -> path.toAbsolutePath().normalize()).orElse(null);
        this.csvSchemaCatalog = csvSchemaCatalog
                .map(path -> path.toAbsolutePath().normalize()).orElse(null);
    }

    /**
     * Opens an existing project against an explicitly selected source mod.
     *
     * @param source selected source mod
     * @param projectPath project JSON
     * @throws ProjectException on read failure
     */
    public void open(Path source, Path projectPath) throws ProjectException {
        setWorkspace(source, projectPath, workflow.read(projectPath));
    }

    /**
     * Saves current editor drafts to the active project document.
     *
     * @throws ProjectException when no project is active or writing fails
     */
    public void save() throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        workflow.write(projectFile, project);
        editor.markSaved();
    }

    /**
     * Saves and publishes the active project.
     *
     * @param outputRoot overlay output directory
     * @return build result
     * @throws ProjectException on validation or publication failure
     */
    public ProjectBuildResult build(Path outputRoot) throws ProjectException {
        return build(outputRoot, CancellationToken.NONE);
    }

    /**
     * Saves and publishes with cooperative cancellation.
     *
     * @param outputRoot overlay output directory
     * @param cancellation cancellation signal
     * @return build result
     * @throws ProjectException on validation or publication failure
     */
    public ProjectBuildResult build(
            Path outputRoot,
            CancellationToken cancellation) throws ProjectException {
        save();
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        return workflow.build(sourceRoot, this.outputRoot, project, cancellation);
    }

    /**
     * Produces a read-only reconciliation preview for the active project.
     *
     * @return candidate refreshed project and deterministic report
     * @throws ProjectException when no project is open or extraction fails
     */
    public ProjectRefreshResult previewRefresh() throws ProjectException {
        return previewRefresh(CancellationToken.NONE);
    }

    /**
     * Produces a read-only reconciliation preview with cancellation.
     *
     * @param cancellation cancellation signal
     * @return refresh candidate and report
     * @throws ProjectException when no project is open or extraction fails
     */
    public ProjectRefreshResult previewRefresh(CancellationToken cancellation)
            throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        return workflow.refresh(sourceRoot, project, cancellation);
    }

    /**
     * Produces a refresh preview enriched by context-safe translation memory.
     *
     * @param translationMemory SQLite translation-memory database
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @param minimumScore fuzzy score threshold
     * @return candidate and deterministic advisory report
     * @throws ProjectException when no project is open or matching fails
     */
    public ProjectRefreshResult previewRefreshWithTranslationMemory(
            Path translationMemory,
            String sourceLanguage,
            String targetLanguage,
            double minimumScore) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        return workflow.refreshWithTranslationMemory(
                sourceRoot,
                project,
                translationMemory,
                sourceLanguage,
                targetLanguage,
                minimumScore,
                CancellationToken.NONE);
    }

    /**
     * Produces a TM-enriched preview with cancellation.
     *
     * @param translationMemory SQLite translation memory
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @param minimumScore fuzzy score threshold
     * @param cancellation cancellation signal
     * @return candidate and advisory report
     * @throws ProjectException on extraction or matching failure
     */
    public ProjectRefreshResult previewRefreshWithTranslationMemory(
            Path translationMemory,
            String sourceLanguage,
            String targetLanguage,
            double minimumScore,
            CancellationToken cancellation) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        return workflow.refreshWithTranslationMemory(
                sourceRoot,
                project,
                translationMemory,
                sourceLanguage,
                targetLanguage,
                minimumScore,
                cancellation);
    }

    /**
     * Persists a previously previewed refresh candidate and reloads the editor.
     *
     * @param result explicit preview result to apply
     * @throws ProjectException when no project is open or writing fails
     */
    public void applyRefresh(ProjectRefreshResult result) throws ProjectException {
        ensureOpen();
        workflow.write(projectFile, result.project());
        project = result.project();
        editor.load(project);
    }

    /**
     * Writes current drafts to an SSMT-owned recovery directory.
     *
     * @param recoveryRoot recovery directory outside source mods
     * @return recovery snapshot path
     * @throws ProjectException when no project is open or writing fails
     */
    public Path autosave(Path recoveryRoot) throws ProjectException {
        ensureOpen();
        LocalizationProject candidate = editor.applyEdits(project);
        return recovery.snapshot(recoveryRoot, projectFile, candidate);
    }

    /**
     * Loads a crash-recovery snapshot when one exists.
     *
     * @param recoveryRoot recovery directory outside source mods
     * @return whether a snapshot was loaded
     * @throws ProjectException when no project is open or recovery fails
     */
    public boolean recover(Path recoveryRoot) throws ProjectException {
        ensureOpen();
        Optional<LocalizationProject> recovered =
                recovery.recover(recoveryRoot, projectFile);
        if (recovered.isEmpty()) {
            return false;
        }
        project = recovered.orElseThrow();
        editor.load(project);
        return true;
    }

    /**
     * @return whether active editor drafts are unsaved
     */
    public boolean hasUnsavedChanges() {
        return editor.isDirty();
    }

    /**
     * @return active project document path
     */
    public Optional<Path> projectFile() {
        return Optional.ofNullable(projectFile);
    }

    /**
     * @return active source mod root
     */
    public Optional<Path> sourceRoot() {
        return Optional.ofNullable(sourceRoot);
    }

    /** @return last selected patch output path */
    public Optional<Path> outputRoot() {
        return Optional.ofNullable(outputRoot);
    }

    /** @return stable recovery directory for the open project */
    public Optional<Path> recoveryRoot() {
        return projectFile().map(ProjectWorkspaceController::recoveryRootFor);
    }

    /** Resolves the SSMT-owned recovery directory for a project document. */
    public static Path recoveryRootFor(Path projectPath) {
        Path absolute = projectPath.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        return (parent == null ? Path.of(".").toAbsolutePath().normalize() : parent)
                .resolve(".ssmt-recovery");
    }

    /** Returns active paths for presentation without exposing mutable state. */
    public ProjectWorkspaceInfo workspaceInfo(Path translationMemory) {
        return new ProjectWorkspaceInfo(
                sourceRoot(), projectFile(), outputRoot(),
                outputRoot().map(path -> path.resolveSibling(
                        path.getFileName() + "-source-backup")),
                Optional.ofNullable(translationMemory), recoveryRoot(),
                Optional.ofNullable(jsonSchemaCatalog),
                Optional.ofNullable(csvSchemaCatalog));
    }

    /**
     * Opens and integrity-checks an existing SQLite translation-memory catalog.
     *
     * @param database existing database
     * @throws ProjectException when the catalog cannot be safely used
     */
    public void verifyTranslationMemory(Path database) throws ProjectException {
        if (!java.nio.file.Files.isRegularFile(database)) {
            throw new ProjectException(
                    "Translation-memory database does not exist: " + database);
        }
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(database)) {
            if (!memory.verifyIntegrity()) {
                throw new ProjectException(
                        "Translation-memory integrity check failed: " + database);
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not open translation-memory database", exception);
        }
    }

    /**
     * Opens an existing SQLite translation-memory catalog, or creates one when
     * the path does not yet exist. Used for the default-catalog startup path;
     * the explicit "Open Existing Database" action uses
     * {@link #verifyTranslationMemory(Path)} instead, since it must not
     * create a catalog where none was intended.
     *
     * @param database catalog path, existing or not yet created
     * @throws ProjectException when the catalog cannot be safely opened or created
     */
    public void openOrCreateTranslationMemory(Path database) throws ProjectException {
        Path parent = database.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            try {
                java.nio.file.Files.createDirectories(parent);
            } catch (java.io.IOException exception) {
                throw new ProjectException(
                        "Could not create translation-memory directory: " + parent, exception);
            }
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            if (!memory.verifyIntegrity()) {
                throw new ProjectException(
                        "Translation-memory integrity check failed: " + database);
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not open or create translation-memory database", exception);
        }
    }

    /**
     * Reads the selected mod's automatically located metadata JSON.
     *
     * @param source selected mod root
     * @return source defaults
     * @throws ProjectException when metadata cannot be read
     */
    public ProjectSourceDetails inspectSource(Path source) throws ProjectException {
        return projectService.inspectSource(source);
    }

    /**
     * Exports the open project as an external-AI translation package, splitting it across
     * sibling numbered files when it has more entries than {@code maximumEntriesPerPart} so a
     * single AI response is never truncated.
     *
     * @param destination output JSON; sibling parts reuse its name plus an index
     * @param originalModName original display name
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @param maximumEntriesPerPart maximum entries in one file; must be positive
     * @return every file written, in part order
     * @throws ProjectException when no project is open or export fails
     */
    public List<Path> exportAiPackage(
            Path destination,
            String originalModName,
            String sourceLanguage,
            String targetLanguage,
            int maximumEntriesPerPart) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        return aiExchange.exportPackage(
                destination,
                project,
                originalModName,
                sourceLanguage,
                targetLanguage,
                maximumEntriesPerPart);
    }

    /**
     * Imports a validated AI response and optionally adds it to translation memory.
     *
     * @param response response JSON
     * @param translationMemory optional SQLite pool
     * @return import result
     * @throws ProjectException when input does not match the open project
     */
    public AiTranslationImportResult importAiResponse(
            Path response,
            Path translationMemory) throws ProjectException {
        return importAiResponse(response, translationMemory, AiImportPolicy.REVIEW_DRAFTS);
    }

    /** Imports a validated AI response under the user's explicit approval policy. */
    public AiTranslationImportResult importAiResponse(
            Path response,
            Path translationMemory,
            AiImportPolicy policy) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        AiTranslationImportResult result =
                aiExchange.importResponse(response, project, translationMemory, policy);
        project = result.project();
        workflow.write(projectFile, project);
        editor.load(project);
        return result;
    }

    /** Exports a deterministic manual browser-review package. */
    public BrowserAiReviewExport exportBrowserReview(
            Path destination,
            String originalModName,
            String sourceLanguage,
            String targetLanguage,
            int maximumEntriesPerPart,
            String terminology) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        return browserReview.export(destination, project, originalModName, sourceLanguage,
                targetLanguage, maximumEntriesPerPart, terminology);
    }

    /** Imports one independently valid browser-review response as drafts. */
    public AiTranslationImportResult importBrowserReview(Path response) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        AiTranslationImportResult result = browserReview.importResponse(response, project);
        project = result.project();
        workflow.write(projectFile, project);
        editor.load(project);
        return result;
    }

    /** Translates blank entries in bounded batches and atomically saves the project. */
    public ProjectTranslationResult translateProject(
            ProjectTranslationSettings settings,
            ProjectEntryTranslationEngine engine,
            CancellationToken cancellation) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        ProjectTranslationResult result = new ProjectTranslationCoordinator().translate(
                project, settings, java.util.Map.of(), engine, cancellation,
                (partial, completed) -> translationCheckpoints.save(
                        translationCheckpointPath(), partial, completed));
        workflow.write(projectFile, result.project());
        if (!result.retainedMetadata().isEmpty()) {
            Path metadata = projectFile.resolveSibling(
                    projectFile.getFileName() + ".generation.json");
            new TranslationMetadataInterchangeService().writeJson(
                    metadata, result.retainedMetadata());
        }
        project = result.project();
        editor.load(project);
        return result;
    }

    /** Restores the last checkpoint only after source identity validation. */
    public void resumeTranslationCheckpoint() throws ProjectException {
        ensureOpen();
        project = translationCheckpoints.resume(translationCheckpointPath(), project);
        workflow.write(projectFile, project);
        editor.load(project);
    }

    public boolean hasTranslationCheckpoint() {
        return projectFile != null
                && java.nio.file.Files.isRegularFile(translationCheckpointPath());
    }

    private Path translationCheckpointPath() {
        return projectFile.resolveSibling(projectFile.getFileName() + ".translation-checkpoint.json");
    }

    /** Reads retained provider lineage for one row when the optional sidecar exists. */
    public Optional<com.ssmt.tm.TranslationGenerationMetadata> generationMetadata(
            TranslationRowId id) throws ProjectException {
        ensureOpen();
        Path sidecar = projectFile.resolveSibling(projectFile.getFileName() + ".generation.json");
        if (!java.nio.file.Files.isRegularFile(sidecar)) {
            return Optional.empty();
        }
        String identity = id.sourceFile().toString().replace('\\', '/') + "#" + id.key();
        return Optional.ofNullable(
                new TranslationMetadataInterchangeService().readJson(sidecar).get(identity));
    }

    /** Exports a deterministic CSV report without changing project or source files. */
    public void exportTranslationReport(Path destination) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        new com.ssmt.project.TranslationReportExporter().write(destination, project);
    }

    /** Loads a data-only glossary and reports conflicts without applying changes. */
    public List<com.ssmt.project.GlossaryConflict> auditGlossary(Path glossary)
            throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        var document = new com.ssmt.project.GlossaryInterchangeService().read(glossary);
        return new com.ssmt.project.GlossaryConflictAuditor().inspect(project, document);
    }

    /**
     * Compares another catalog with the active destination without writing.
     */
    public TranslationMemoryMergeResult compareTranslationMemories(
            Path source,
            Path destination) throws ProjectException {
        try {
            return memoryMerge.compare(source, destination);
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not compare translation memories: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Conservatively merges missing and strictly higher-preference entries.
     */
    public TranslationMemoryMergeResult mergeTranslationMemories(
            Path source,
            Path destination) throws ProjectException {
        try {
            return memoryMerge.merge(source, destination);
        } catch (TranslationMemoryException exception) {
            throw new ProjectException(
                    "Could not merge translation memories: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Suggests the primary language of the currently loaded source strings.
     *
     * @return BCP-47 code or {@code und} when uncertain
     * @throws ProjectException when no project is open
     */
    public String detectSourceLanguage() throws ProjectException {
        ensureOpen();
        return languageDetector.detect(project.entries());
    }

    /**
     * Computes overall and per-file translation completion for the active project.
     *
     * @return current translation coverage
     * @throws ProjectException when no project is open
     */
    public TranslationCoverageReport translationCoverage() throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        return new TranslationCoverageAuditor().audit(project);
    }

    /**
     * Checks the active project's translated text against a Starsector BMFont file.
     *
     * @param fontFile Starsector {@code .fnt} file to check against
     * @return entries containing characters the font cannot render
     * @throws ProjectException when no project is open or the font cannot be read
     */
    public List<FontCoverageFinding> checkFontCoverage(Path fontFile) throws ProjectException {
        ensureOpen();
        project = editor.applyEdits(project);
        try {
            BmFontGlyphSet font = BmFontGlyphSet.read(fontFile);
            return new FontCoverageAuditor().audit(project, font);
        } catch (SsmtParseException exception) {
            throw new ProjectException("Could not read font file", exception);
        }
    }

    private void setWorkspace(Path source, Path path, LocalizationProject value) {
        sourceRoot = source.toAbsolutePath().normalize();
        projectFile = path.toAbsolutePath().normalize();
        project = value;
        outputRoot = null;
        jsonSchemaCatalog = null;
        csvSchemaCatalog = null;
        editor.load(value);
    }

    private void ensureOpen() throws ProjectException {
        if (project == null || projectFile == null || sourceRoot == null) {
            throw new ProjectException("No localization project is open");
        }
    }

    interface ProjectWorkflow {
        LocalizationProject create(
                Path sourceRoot,
                String patchId,
                String patchName,
                Optional<Path> jsonSchemaCatalog,
                Optional<Path> csvSchemaCatalog,
                CancellationToken cancellation) throws ProjectException;

        LocalizationProject read(Path source) throws ProjectException;

        void write(Path destination, LocalizationProject project) throws ProjectException;

        ProjectBuildResult build(
                Path sourceRoot,
                Path outputRoot,
                LocalizationProject project,
                CancellationToken cancellation) throws ProjectException;

        ProjectRefreshResult refresh(
                Path sourceRoot,
                LocalizationProject project,
                CancellationToken cancellation) throws ProjectException;

        ProjectRefreshResult refreshWithTranslationMemory(
                Path sourceRoot,
                LocalizationProject project,
                Path translationMemory,
                String sourceLanguage,
                String targetLanguage,
                double minimumScore,
                CancellationToken cancellation) throws ProjectException;
    }

    private record ServiceWorkflow(LocalizationProjectService service)
            implements ProjectWorkflow {
        @Override
        public LocalizationProject create(
                Path sourceRoot,
                String patchId,
                String patchName,
                Optional<Path> jsonSchemaCatalog,
                Optional<Path> csvSchemaCatalog,
                CancellationToken cancellation) throws ProjectException {
            return jsonSchemaCatalog.isPresent() || csvSchemaCatalog.isPresent()
                    ? service.createWithSchemas(
                            sourceRoot,
                            patchId,
                            patchName,
                            jsonSchemaCatalog,
                            csvSchemaCatalog,
                            cancellation)
                    : service.create(
                            sourceRoot, patchId, patchName, cancellation);
        }

        @Override
        public LocalizationProject read(Path source) throws ProjectException {
            return service.read(source);
        }

        @Override
        public void write(Path destination, LocalizationProject project)
                throws ProjectException {
            service.write(destination, project);
        }

        @Override
        public ProjectBuildResult build(
                Path sourceRoot,
                Path outputRoot,
                LocalizationProject project,
                CancellationToken cancellation) throws ProjectException {
            return service.build(sourceRoot, outputRoot, project, cancellation);
        }

        @Override
        public ProjectRefreshResult refresh(
                Path sourceRoot,
                LocalizationProject project,
                CancellationToken cancellation) throws ProjectException {
            return service.refresh(sourceRoot, project, cancellation);
        }

        @Override
        public ProjectRefreshResult refreshWithTranslationMemory(
                Path sourceRoot,
                LocalizationProject project,
                Path translationMemory,
                String sourceLanguage,
                String targetLanguage,
                double minimumScore,
                CancellationToken cancellation) throws ProjectException {
            return service.refreshWithTranslationMemory(
                    sourceRoot,
                    project,
                    translationMemory,
                    sourceLanguage,
                    targetLanguage,
                    minimumScore,
                    cancellation);
        }
    }
}
