package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.CancellationToken;
import com.ssmt.core.RuntimeBudgets;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.model.ModInfo;
import com.ssmt.core.plugin.FileExtractor;
import com.ssmt.extractor.ExtractionCoordinator;
import com.ssmt.extractor.bytecode.ClassStringExtractor;
import com.ssmt.extractor.csv.ConfiguredCsvFileExtractor;
import com.ssmt.extractor.csv.OptInCsvSchemaCatalog;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import com.ssmt.extractor.json.ConfiguredJsonFileExtractor;
import com.ssmt.extractor.json.OptInJsonSchemaCatalog;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import com.ssmt.patcher.ClassFileInjector;
import com.ssmt.patcher.PatchArtifact;
import com.ssmt.patcher.PatchBuildResult;
import com.ssmt.patcher.PatchBuilder;
import com.ssmt.patcher.PatchBuilderException;
import com.ssmt.patcher.PatchRequest;
import com.ssmt.patcher.StandardFileInjector;
import com.ssmt.patcher.TranslationReplacement;
import com.ssmt.scanner.ModInfoReader;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import com.ssmt.tm.TranslationQuery;
import com.ssmt.validation.TranslationValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Coordinates the complete source-safe localization workflow.
 */
public final class LocalizationProjectService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ModInfoReader modInfoReader = new ModInfoReader();
    private final ExtractionCoordinator extraction = new ExtractionCoordinator(List.of(
            new StandardCsvFileExtractor(),
            new StandardJsonFileExtractor(),
            new ClassStringExtractor()));
    private final TranslationValidator validator = new TranslationValidator();
    private final StandardFileInjector standardInjector = new StandardFileInjector();
    private final ClassFileInjector classInjector = new ClassFileInjector();
    private final PatchBuilder patchBuilder = new PatchBuilder();

    /**
     * Reads the automatically located {@code mod_info.json} for project defaults.
     *
     * @param sourceRoot selected mod root
     * @return source id and display name
     * @throws ProjectException when metadata is missing or malformed
     */
    public ProjectSourceDetails inspectSource(Path sourceRoot) throws ProjectException {
        try {
            ModInfo mod = modInfoReader.read(sourceRoot);
            return new ProjectSourceDetails(mod.id(), mod.name());
        } catch (SsmtParseException exception) {
            throw new ProjectException("Could not load mod_info.json", exception);
        }
    }

    /**
     * Extracts a source mod into a portable editable project.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @return deterministic project
     * @throws ProjectException when metadata or extraction fails
     */
    public LocalizationProject create(Path sourceRoot, String patchId, String patchName)
            throws ProjectException {
        return create(
                sourceRoot,
                patchId,
                patchName,
                extraction,
                CancellationToken.NONE);
    }

    /**
     * Extracts a project with cooperative cancellation.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param cancellation cancellation signal
     * @return deterministic project
     * @throws ProjectException when metadata or extraction fails
     */
    public LocalizationProject create(
            Path sourceRoot,
            String patchId,
            String patchName,
            CancellationToken cancellation) throws ProjectException {
        return create(sourceRoot, patchId, patchName, extraction, cancellation);
    }

    /**
     * Extracts a source mod with an explicit custom JSON schema catalog.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param schemaCatalog opt-in JSON schema catalog
     * @return deterministic project
     * @throws ProjectException when metadata, schema, or extraction fails
     */
    public LocalizationProject createWithJsonSchema(
            Path sourceRoot,
            String patchId,
            String patchName,
            Path schemaCatalog) throws ProjectException {
        return createWithJsonSchema(
                sourceRoot,
                patchId,
                patchName,
                schemaCatalog,
                CancellationToken.NONE);
    }

    /**
     * Extracts with an explicit JSON schema and cooperative cancellation.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param schemaCatalog opt-in JSON schema catalog
     * @param cancellation cancellation signal
     * @return deterministic project
     * @throws ProjectException when schema, metadata, or extraction fails
     */
    public LocalizationProject createWithJsonSchema(
            Path sourceRoot,
            String patchId,
            String patchName,
            Path schemaCatalog,
            CancellationToken cancellation) throws ProjectException {
        return createWithSchemas(
                sourceRoot,
                patchId,
                patchName,
                Optional.of(schemaCatalog),
                Optional.empty(),
                cancellation);
    }

    /**
     * Extracts a source mod with an explicit custom CSV schema catalog.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param schemaCatalog opt-in CSV schema catalog
     * @return deterministic project
     * @throws ProjectException when metadata, schema, or extraction fails
     */
    public LocalizationProject createWithCsvSchema(
            Path sourceRoot,
            String patchId,
            String patchName,
            Path schemaCatalog) throws ProjectException {
        return createWithCsvSchema(
                sourceRoot,
                patchId,
                patchName,
                schemaCatalog,
                CancellationToken.NONE);
    }

    /**
     * Extracts with an explicit CSV schema and cooperative cancellation.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param schemaCatalog opt-in CSV schema catalog
     * @param cancellation cancellation signal
     * @return deterministic project
     * @throws ProjectException when schema, metadata, or extraction fails
     */
    public LocalizationProject createWithCsvSchema(
            Path sourceRoot,
            String patchId,
            String patchName,
            Path schemaCatalog,
            CancellationToken cancellation) throws ProjectException {
        return createWithSchemas(
                sourceRoot,
                patchId,
                patchName,
                Optional.empty(),
                Optional.of(schemaCatalog),
                cancellation);
    }

    /**
     * Extracts a source mod with explicit custom JSON and/or CSV schema catalogs.
     * Either or both may be present; an absent catalog leaves the corresponding
     * standard extraction behavior unchanged.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param jsonSchemaCatalog optional opt-in JSON schema catalog
     * @param csvSchemaCatalog optional opt-in CSV schema catalog
     * @return deterministic project
     * @throws ProjectException when metadata, schema, or extraction fails
     */
    public LocalizationProject createWithSchemas(
            Path sourceRoot,
            String patchId,
            String patchName,
            Optional<Path> jsonSchemaCatalog,
            Optional<Path> csvSchemaCatalog) throws ProjectException {
        return createWithSchemas(
                sourceRoot,
                patchId,
                patchName,
                jsonSchemaCatalog,
                csvSchemaCatalog,
                CancellationToken.NONE);
    }

    /**
     * Extracts with explicit custom schema catalogs and cooperative cancellation.
     *
     * @param sourceRoot source mod root
     * @param patchId generated patch id
     * @param patchName generated patch name
     * @param jsonSchemaCatalog optional opt-in JSON schema catalog
     * @param csvSchemaCatalog optional opt-in CSV schema catalog
     * @param cancellation cancellation signal
     * @return deterministic project
     * @throws ProjectException when schema, metadata, or extraction fails
     */
    public LocalizationProject createWithSchemas(
            Path sourceRoot,
            String patchId,
            String patchName,
            Optional<Path> jsonSchemaCatalog,
            Optional<Path> csvSchemaCatalog,
            CancellationToken cancellation) throws ProjectException {
        List<FileExtractor> extractors = new ArrayList<>(List.of(
                new StandardCsvFileExtractor(),
                new StandardJsonFileExtractor(),
                new ClassStringExtractor()));
        if (jsonSchemaCatalog.isPresent()) {
            try {
                extractors.add(new ConfiguredJsonFileExtractor(
                        new OptInJsonSchemaCatalog().read(jsonSchemaCatalog.orElseThrow())));
            } catch (SsmtParseException | IllegalArgumentException exception) {
                throw new ProjectException("Could not load opt-in JSON schema", exception);
            }
        }
        if (csvSchemaCatalog.isPresent()) {
            try {
                extractors.add(new ConfiguredCsvFileExtractor(
                        new OptInCsvSchemaCatalog().read(csvSchemaCatalog.orElseThrow())));
            } catch (SsmtParseException | IllegalArgumentException exception) {
                throw new ProjectException("Could not load opt-in CSV schema", exception);
            }
        }
        return create(
                sourceRoot,
                patchId,
                patchName,
                new ExtractionCoordinator(extractors),
                cancellation);
    }

    /**
     * Re-extracts an updated source and produces a non-persisted reconciliation candidate.
     * Suggestions are advisory and are never copied into the refreshed project.
     *
     * @param sourceRoot updated source mod root
     * @param project existing localization project
     * @return refreshed candidate and deterministic dry-run report
     * @throws ProjectException when metadata or extraction fails
     */
    public ProjectRefreshResult refresh(
            Path sourceRoot,
            LocalizationProject project) throws ProjectException {
        return refresh(sourceRoot, project, CancellationToken.NONE);
    }

    /**
     * Reconciles with cooperative cancellation at entry boundaries.
     *
     * @param sourceRoot updated source mod root
     * @param project existing localization project
     * @param cancellation cancellation signal
     * @return refreshed candidate and report
     * @throws ProjectException when extraction fails
     */
    public ProjectRefreshResult refresh(
            Path sourceRoot,
            LocalizationProject project,
            CancellationToken cancellation) throws ProjectException {
        if (cancellation == null) {
            throw new IllegalArgumentException("cancellation must not be null");
        }
        cancellation.throwIfCancellationRequested();
        LocalizationProject extracted =
                create(
                        sourceRoot,
                        project.patchId(),
                        project.patchName(),
                        extraction,
                        cancellation);
        if (!extracted.sourceModId().equals(project.sourceModId())) {
            throw new ProjectException("Project source mod id does not match selected source");
        }

        Map<EntryIdentity, ProjectEntry> previous = new LinkedHashMap<>();
        for (ProjectEntry entry : project.entries()) {
            previous.put(identity(entry), entry);
        }
        Set<EntryIdentity> matched = new HashSet<>();
        List<ProjectEntry> refreshed = new ArrayList<>();
        List<ReconciliationEntry> findings = new ArrayList<>();
        for (ProjectEntry current : extracted.entries()) {
            cancellation.throwIfCancellationRequested();
            EntryIdentity currentIdentity = identity(current);
            ProjectEntry exact = previous.get(currentIdentity);
            if (exact != null) {
                matched.add(currentIdentity);
                if (exact.originalText().equals(current.originalText())) {
                    ProjectEntry preserved =
                            current.withTranslation(
                                    exact.translatedText(), exact.provenance());
                    refreshed.add(preserved);
                    findings.add(finding(
                            ReconciliationStatus.UNCHANGED,
                            preserved,
                            exact.translatedText(),
                            List.of()));
                } else {
                    refreshed.add(current);
                    findings.add(finding(
                            ReconciliationStatus.CHANGED,
                            current,
                            exact.translatedText(),
                            suggestion(exact)));
                }
                continue;
            }

            List<ProjectEntry> candidates = project.entries().stream()
                    .filter(entry -> !matched.contains(identity(entry)))
                    .filter(entry -> entry.originalText().equals(current.originalText()))
                    .filter(entry -> !entry.translatedText().isBlank())
                    .toList();
            refreshed.add(current);
            if (candidates.size() == 1) {
                ProjectEntry candidate = candidates.getFirst();
                matched.add(identity(candidate));
                findings.add(finding(
                        ReconciliationStatus.CHANGED,
                        current,
                        candidate.translatedText(),
                        suggestion(candidate)));
            } else if (candidates.size() > 1) {
                findings.add(finding(
                        ReconciliationStatus.CONFLICTED,
                        current,
                        "",
                        candidates.stream()
                                .map(ProjectEntry::translatedText)
                                .toList()));
            } else {
                findings.add(finding(
                        ReconciliationStatus.ADDED, current, "", List.of()));
            }
        }
        for (ProjectEntry old : project.entries()) {
            cancellation.throwIfCancellationRequested();
            if (!matched.contains(identity(old))) {
                findings.add(finding(
                        ReconciliationStatus.REMOVED,
                        old,
                        old.translatedText(),
                        List.of()));
            }
        }
        return new ProjectRefreshResult(
                project.withEntries(refreshed),
                new ReconciliationReport(findings));
    }

    /**
     * Adds context-bounded translation-memory suggestions to a refresh preview.
     * Suggestions remain advisory and are never copied into translated text.
     *
     * @param sourceRoot updated source mod root
     * @param project existing localization project
     * @param translationMemory SQLite translation-memory document
     * @param sourceLanguage source language identifier
     * @param targetLanguage target language identifier
     * @param minimumScore inclusive fuzzy-match threshold
     * @return refreshed candidate with deterministic suggestions
     * @throws ProjectException on extraction or translation-memory failure
     */
    public ProjectRefreshResult refreshWithTranslationMemory(
            Path sourceRoot,
            LocalizationProject project,
            Path translationMemory,
            String sourceLanguage,
            String targetLanguage,
            double minimumScore) throws ProjectException {
        return refreshWithTranslationMemory(
                sourceRoot,
                project,
                translationMemory,
                sourceLanguage,
                targetLanguage,
                minimumScore,
                CancellationToken.NONE);
    }

    /**
     * Adds context-safe TM suggestions with cooperative cancellation.
     *
     * @param sourceRoot updated source mod root
     * @param project existing project
     * @param translationMemory SQLite translation memory
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @param minimumScore fuzzy score threshold
     * @param cancellation cancellation signal
     * @return refreshed candidate with advisory suggestions
     * @throws ProjectException on extraction or matching failure
     */
    public ProjectRefreshResult refreshWithTranslationMemory(
            Path sourceRoot,
            LocalizationProject project,
            Path translationMemory,
            String sourceLanguage,
            String targetLanguage,
            double minimumScore,
            CancellationToken cancellation) throws ProjectException {
        ProjectRefreshResult base = refresh(sourceRoot, project, cancellation);
        List<ReconciliationEntry> enriched = new ArrayList<>();
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(translationMemory)) {
            for (ReconciliationEntry finding : base.report().entries()) {
                cancellation.throwIfCancellationRequested();
                if (finding.status() == ReconciliationStatus.UNCHANGED
                        || finding.status() == ReconciliationStatus.REMOVED) {
                    enriched.add(finding);
                    continue;
                }
                List<String> suggestions = new ArrayList<>(finding.suggestions());
                suggestions.addAll(memory.findMatches(new TranslationQuery(
                                finding.originalText(),
                                sourceLanguage,
                                targetLanguage,
                                context(finding.sourceFile()),
                                minimumScore,
                                5))
                        .stream()
                        .map(match -> match.entry().translatedText())
                        .toList());
                enriched.add(new ReconciliationEntry(
                        finding.status(),
                        finding.sourceFile(),
                        finding.key(),
                        finding.originalText(),
                        finding.previousTranslation(),
                        suggestions));
            }
        } catch (TranslationMemoryException | IllegalArgumentException exception) {
            throw new ProjectException(
                    "Could not obtain translation-memory refresh suggestions", exception);
        }
        return new ProjectRefreshResult(
                base.project(), new ReconciliationReport(enriched));
    }

    private LocalizationProject create(
            Path sourceRoot,
            String patchId,
            String patchName,
            ExtractionCoordinator coordinator,
            CancellationToken cancellation) throws ProjectException {
        try {
            cancellation.throwIfCancellationRequested();
            ModInfo mod = modInfoReader.read(sourceRoot);
            List<ProjectEntry> entries = coordinator
                    .extractMod(
                            mod.id(),
                            mod.sourceDirectory(),
                            cancellation,
                            RuntimeBudgets.DEFAULTS)
                    .strings()
                    .stream()
                    .map(LocalizationProjectService::entry)
                    .toList();
            AuthorLocalizationReport authorReport =
                    new AuthorLocalizationDetector().detect(entries);
            if (!authorReport.pairs().isEmpty()) {
                Map<String, AuthorLocalizationPair> pairs = new LinkedHashMap<>();
                Set<String> translatedIdentities = new HashSet<>();
                for (AuthorLocalizationPair pair : authorReport.pairs()) {
                    pairs.put(artifactIdentity(pair.source()), pair);
                    translatedIdentities.add(artifactIdentity(pair.authorTranslation()));
                }
                entries = entries.stream()
                        .filter(value -> !translatedIdentities.contains(
                                artifactIdentity(value)))
                        .map(value -> {
                            AuthorLocalizationPair pair = pairs.get(
                                    artifactIdentity(value));
                            return pair == null
                                    ? value
                                    : value.withTranslation(
                                            pair.authorTranslation().originalText(),
                                            com.ssmt.core.model.TranslationProvenance
                                                    .AUTHOR_LOCALIZATION);
                        })
                        .toList();
            }
            return new LocalizationProject(
                    LocalizationProject.CURRENT_SCHEMA_VERSION,
                    mod.id(),
                    patchId,
                    patchName,
                    entries);
        } catch (SsmtParseException | IllegalArgumentException exception) {
            throw new ProjectException("Could not create localization project", exception);
        }
    }

    /**
     * Writes deterministic UTF-8 project JSON.
     *
     * @param destination output document
     * @param project project data
     * @throws ProjectException on write failure
     */
    public void write(Path destination, LocalizationProject project) throws ProjectException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", project.schemaVersion());
        root.put("sourceModId", project.sourceModId());
        root.put("patchId", project.patchId());
        root.put("patchName", project.patchName());
        ArrayNode entries = root.putArray("entries");
        for (ProjectEntry entry : project.entries()) {
            ObjectNode node = entries.addObject();
            node.put("sourceFile", entry.sourceFile().toString().replace('\\', '/'));
            node.put("key", entry.key());
            node.put("originalText", entry.originalText());
            node.put("translatedText", entry.translatedText());
            node.put("provenance", entry.provenance().name());
        }
        Path target = destination.toAbsolutePath().normalize();
        Path staging = target.resolveSibling(target.getFileName() + ".ssmt-stage");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), root);
            try {
                Files.move(
                        staging,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not write project " + destination, exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // A failed staging cleanup must not hide the original write result.
            }
        }
    }

    /**
     * Reads and validates one versioned project document.
     *
     * @param source project document
     * @return validated project
     * @throws ProjectException on malformed or unsupported content
     */
    public LocalizationProject read(Path source) throws ProjectException {
        try {
            JsonNode root = JSON.readTree(source.toFile());
            List<ProjectEntry> entries = new ArrayList<>();
            for (JsonNode node : required(root, "entries")) {
                entries.add(new ProjectEntry(
                        Path.of(text(node, "sourceFile")),
                        text(node, "key"),
                        text(node, "originalText"),
                        text(node, "translatedText"),
                        node.has("provenance")
                                ? com.ssmt.core.model.TranslationProvenance.valueOf(
                                        text(node, "provenance"))
                                : com.ssmt.core.model.TranslationProvenance.MANUAL_IMPORT));
            }
            return new LocalizationProject(
                    required(root, "schemaVersion").intValue(),
                    text(root, "sourceModId"),
                    text(root, "patchId"),
                    text(root, "patchName"),
                    entries);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read project " + source, exception);
        }
    }

    /**
     * Validates and publishes one complete translation overlay.
     *
     * @param sourceRoot source mod root
     * @param outputRoot generated overlay root
     * @param project translated project
     * @return build summary
     * @throws ProjectException on validation, stale source, or publication failure
     */
    public ProjectBuildResult build(
            Path sourceRoot,
            Path outputRoot,
            LocalizationProject project) throws ProjectException {
        return build(sourceRoot, outputRoot, project, CancellationToken.NONE);
    }

    /**
     * Validates and publishes with cooperative cancellation before publication.
     *
     * @param sourceRoot source mod root
     * @param outputRoot generated overlay root
     * @param project translated project
     * @param cancellation cancellation signal
     * @return build summary
     * @throws ProjectException on validation or publication failure
     */
    public ProjectBuildResult build(
            Path sourceRoot,
            Path outputRoot,
            LocalizationProject project,
            CancellationToken cancellation) throws ProjectException {
        if (cancellation == null) {
            throw new IllegalArgumentException("cancellation must not be null");
        }
        cancellation.throwIfCancellationRequested();
        ModInfo mod;
        try {
            mod = modInfoReader.read(sourceRoot);
        } catch (SsmtParseException exception) {
            throw new ProjectException("Could not read source mod", exception);
        }
        if (!mod.id().equals(project.sourceModId())) {
            throw new ProjectException("Project source mod id does not match selected source");
        }
        Map<Path, List<TranslationReplacement>> grouped = new LinkedHashMap<>();
        for (ProjectEntry entry : project.entries()) {
            cancellation.throwIfCancellationRequested();
            if (entry.originalText().isBlank()
                    && entry.translatedText().isBlank()) {
                continue;
            }
            validate(entry);
            grouped.computeIfAbsent(entry.sourceFile(), ignored -> new ArrayList<>())
                    .add(new TranslationReplacement(
                            entry.sourceFile(),
                            entry.key(),
                            entry.originalText(),
                            entry.translatedText()));
        }
        List<PatchArtifact> artifacts = new ArrayList<>();
        try {
            for (List<TranslationReplacement> replacements : grouped.values()) {
                cancellation.throwIfCancellationRequested();
                Path relative = replacements.getFirst().sourceFile();
                if (relative.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".class")) {
                    artifacts.add(classInjector.inject(sourceRoot, replacements));
                } else {
                    artifacts.add(standardInjector.inject(sourceRoot, replacements));
                }
            }
            cancellation.throwIfCancellationRequested();
            PatchBuildResult result = patchBuilder.build(new PatchRequest(
                    sourceRoot,
                    outputRoot,
                    project.patchId(),
                    project.patchName(),
                    project.sourceModId(),
                    mod.name(),
                    mod.gameVersion(),
                    artifacts));
            Path report = outputRoot.resolve("Project Go Changes.csv");
            if (result.changed() || !Files.isRegularFile(report)) {
                new TranslationReportExporter().write(report, project);
            }
            return new ProjectBuildResult(result.changed(), result.artifactCount());
        } catch (PatchBuilderException | IllegalArgumentException exception) {
            throw new ProjectException(
                    "Could not build localization project: " + exception.getMessage(),
                    exception);
        }
    }

    /** Checks every build precondition without writing a translated copy. */
    public ProjectBuildPreview previewBuild(Path sourceRoot, LocalizationProject project)
            throws ProjectException {
        ModInfo mod;
        try {
            mod = modInfoReader.read(sourceRoot);
        } catch (SsmtParseException exception) {
            throw new ProjectException("Could not read source mod", exception);
        }
        if (!mod.id().equals(project.sourceModId())) {
            throw new ProjectException("Project source mod id does not match selected source");
        }
        int translated = 0;
        Set<Path> files = new HashSet<>();
        for (ProjectEntry entry : project.entries()) {
            if (entry.translatedText().isBlank()) {
                continue;
            }
            validate(entry);
            translated++;
            files.add(entry.sourceFile());
        }
        return new ProjectBuildPreview(translated, project.entries().size() - translated, files.size());
    }

    private void validate(ProjectEntry entry) throws ProjectException {
        if (entry.translatedText().isBlank()) {
            throw new ProjectException("Blank translation at " + entry.key());
        }
        var issues = validator.validate(entry.originalText(), entry.translatedText());
        if (!issues.isEmpty()) {
            throw new ProjectException(
                    "Invalid translation at " + entry.key() + ": " + issues.getFirst().message());
        }
    }

    private static ProjectEntry entry(ExtractedString extracted) {
        return new ProjectEntry(
                extracted.sourceFile(),
                extracted.key(),
                extracted.originalText(),
                "");
    }

    private static String artifactIdentity(ProjectEntry entry) {
        return entry.sourceFile().toString().replace('\\', '/')
                + "#" + entry.key();
    }

    private static ReconciliationEntry finding(
            ReconciliationStatus status,
            ProjectEntry entry,
            String previousTranslation,
            List<String> suggestions) {
        return new ReconciliationEntry(
                status,
                entry.sourceFile(),
                entry.key(),
                entry.originalText(),
                previousTranslation,
                suggestions);
    }

    private static List<String> suggestion(ProjectEntry entry) {
        return entry.translatedText().isBlank()
                ? List.of()
                : List.of(entry.translatedText());
    }

    private static EntryIdentity identity(ProjectEntry entry) {
        return new EntryIdentity(entry.sourceFile(), entry.key());
    }

    private static String context(Path sourceFile) {
        return sourceFile.toString().replace('\\', '/');
    }

    private static JsonNode required(JsonNode parent, String name) {
        JsonNode value = parent == null ? null : parent.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing project field " + name);
        }
        return value;
    }

    private static String text(JsonNode parent, String name) {
        JsonNode value = required(parent, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Project field is not text: " + name);
        }
        return value.textValue();
    }

    private record EntryIdentity(Path sourceFile, String key) {
    }
}
