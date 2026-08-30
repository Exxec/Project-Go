package com.ssmt.cli;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.project.AiImportPolicy;
import com.ssmt.project.AiTranslationExchangeService;
import com.ssmt.project.AiTranslationImportResult;
import com.ssmt.project.FileTranslationCoverage;
import com.ssmt.project.FontCoverageAuditor;
import com.ssmt.project.FontCoverageFinding;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.ReconciliationStatus;
import com.ssmt.project.TranslationCoverageAuditor;
import com.ssmt.project.TranslationCoverageReport;
import com.ssmt.validation.font.BmFontGlyphSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Creates and builds portable localization projects.
 */
@Command(
        name = "project",
        description = "Create or build a portable localization project.",
        subcommands = {
            ProjectCommand.Create.class,
            ProjectCommand.Build.class,
            ProjectCommand.Refresh.class,
            ProjectCommand.CheckFonts.class,
            ProjectCommand.Coverage.class,
            ProjectCommand.ImportAiResponse.class
        })
public final class ProjectCommand implements Runnable {
    @Override
    public void run() {
        // Picocli displays this command's usage when no operation is selected.
    }

    @Command(
            name = "refresh",
            description = "Dry-run reconciliation against an updated source mod.")
    static final class Refresh implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Refresh.class);

        @Parameters(index = "0", description = "Updated source Starsector mod directory.")
        private Path source;

        @Parameters(index = "1", description = "Existing project JSON.")
        private Path projectFile;

        @Option(
                names = "--apply",
                description = "Transactionally replace the project after reporting.")
        private boolean apply;

        @Option(names = "--tm", description = "Optional SQLite translation memory.")
        private Optional<Path> translationMemory = Optional.empty();

        @Option(
                names = "--source-language",
                defaultValue = "en",
                description = "Translation-memory source language.")
        private String sourceLanguage;

        @Option(
                names = "--target-language",
                defaultValue = "en",
                description = "Translation-memory target language.")
        private String targetLanguage;

        @Option(
                names = "--minimum-score",
                defaultValue = "0.8",
                description = "Minimum translation-memory fuzzy score.")
        private double minimumScore;

        @Override
        public Integer call() {
            LocalizationProjectService service = new LocalizationProjectService();
            try {
                LocalizationProject project = service.read(projectFile);
                ProjectRefreshResult result = translationMemory.isPresent()
                        ? service.refreshWithTranslationMemory(
                                source,
                                project,
                                translationMemory.orElseThrow(),
                                sourceLanguage,
                                targetLanguage,
                                minimumScore)
                        : service.refresh(source, project);
                for (ReconciliationStatus status : ReconciliationStatus.values()) {
                    LOG.info("{}: {}", status, result.report().count(status));
                }
                result.report().entries().stream()
                        .filter(entry -> !entry.suggestions().isEmpty())
                        .forEach(entry -> LOG.info(
                                "{} {} {} suggestion(s): {}",
                                entry.status(),
                                entry.sourceFile(),
                                entry.key(),
                                entry.suggestions().size()));
                if (apply) {
                    service.write(projectFile, result.project());
                    LOG.info("Refreshed project: {}", projectFile);
                } else {
                    LOG.info("Dry run only; project was not changed");
                }
                return 0;
            } catch (ProjectException exception) {
                LOG.error(CliDiagnostics.explain("Project refresh", exception));
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Extract a mod into editable project JSON.")
    static final class Create implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Create.class);

        @Parameters(index = "0", description = "Source Starsector mod directory.")
        private Path source;

        @Parameters(index = "1", description = "Destination project JSON.")
        private Path destination;

        @Option(names = "--patch-id", required = true, description = "Generated patch mod id.")
        private String patchId;

        @Option(
                names = "--patch-name",
                required = true,
                description = "Generated patch display name.")
        private String patchName;

        @Option(
                names = "--json-schema",
                description = "Optional versioned custom JSON extraction schema.")
        private Optional<Path> jsonSchema = Optional.empty();

        @Option(
                names = "--csv-schema",
                description = "Optional versioned custom CSV extraction schema.")
        private Optional<Path> csvSchema = Optional.empty();

        @Override
        public Integer call() {
            LocalizationProjectService service = new LocalizationProjectService();
            try {
                LocalizationProject project = jsonSchema.isPresent() || csvSchema.isPresent()
                        ? service.createWithSchemas(
                                source, patchId, patchName, jsonSchema, csvSchema)
                        : service.create(source, patchId, patchName);
                service.write(destination, project);
                LOG.info("Created project with {} translation(s): {}",
                        project.entries().size(), destination);
                return 0;
            } catch (ProjectException exception) {
                LOG.error(CliDiagnostics.explain("Project creation", exception));
                return 1;
            }
        }

    }

    @Command(name = "build", description = "Validate and publish a translation overlay.")
    static final class Build implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Build.class);

        @Parameters(index = "0", description = "Source Starsector mod directory.")
        private Path source;

        @Parameters(index = "1", description = "Translated project JSON.")
        private Path projectFile;

        @Parameters(index = "2", description = "Translated clone directory.")
        private Path output;

        @Override
        public Integer call() {
            LocalizationProjectService service = new LocalizationProjectService();
            try {
                LocalizationProject project = service.read(projectFile);
                ProjectBuildResult result = service.build(source, output, project);
                LOG.info("Translated clone {} with {} translated file(s): {}; "
                                + "pristine source backup: {}-source-backup",
                        result.changed() ? "published" : "unchanged",
                        result.artifactCount(),
                        output,
                        output);
                return 0;
            } catch (ProjectException exception) {
                LOG.error(CliDiagnostics.explain("Project build", exception));
                return 1;
            }
        }
    }

    @Command(
            name = "import-ai-response",
            description = "Apply a reviewable external-AI translation response to a project.")
    static final class ImportAiResponse implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(ImportAiResponse.class);

        @Parameters(index = "0", description = "Existing project JSON.")
        private Path projectFile;

        @Parameters(
                index = "1",
                description = "AI response JSON produced from an SSMT AI export package.")
        private Path response;

        @Option(
                names = "--tm",
                description = "Optional SQLite translation memory to record drafts into.")
        private Optional<Path> translationMemory = Optional.empty();

        @Option(
                names = "--approve",
                description = "Mark every validated nonblank translation approved instead of "
                        + "leaving it a reviewable AI draft.")
        private boolean approve;

        @Override
        public Integer call() {
            LocalizationProjectService service = new LocalizationProjectService();
            try {
                LocalizationProject project = service.read(projectFile);
                AiTranslationImportResult result = new AiTranslationExchangeService().importResponse(
                        response,
                        project,
                        translationMemory.orElse(null),
                        approve ? AiImportPolicy.APPROVE_ALL_VALIDATED : AiImportPolicy.REVIEW_DRAFTS);
                service.write(projectFile, result.project());
                LOG.info("Imported {} translation(s), {} left unchanged{}: {}",
                        result.importedEntries(),
                        result.skippedEntries(),
                        result.patchNameUpdated() ? " (patch name updated)" : "",
                        projectFile);
                return 0;
            } catch (ProjectException exception) {
                LOG.error(CliDiagnostics.explain("AI response import", exception));
                return 1;
            }
        }
    }

    @Command(
            name = "check-fonts",
            description = "Warn about translated text a Starsector bitmap font cannot render.")
    static final class CheckFonts implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(CheckFonts.class);

        @Parameters(index = "0", description = "Translated project JSON.")
        private Path projectFile;

        @Parameters(index = "1", description = "Starsector BMFont .fnt file to check against.")
        private Path fontFile;

        @Override
        public Integer call() {
            LocalizationProjectService service = new LocalizationProjectService();
            try {
                LocalizationProject project = service.read(projectFile);
                BmFontGlyphSet font = BmFontGlyphSet.read(fontFile);
                List<FontCoverageFinding> findings =
                        new FontCoverageAuditor().audit(project, font);
                for (FontCoverageFinding finding : findings) {
                    LOG.warn("{} {} missing glyphs for: {}",
                            finding.sourceFile(), finding.key(), finding.missingCharacters());
                }
                LOG.info("Font '{}': {} finding(s) across {} entries",
                        font.faceName(), findings.size(), project.entries().size());
                return findings.isEmpty() ? 0 : 1;
            } catch (ProjectException | SsmtParseException exception) {
                LOG.error(CliDiagnostics.explain("Font coverage check", exception));
                return 1;
            }
        }
    }

    @Command(
            name = "coverage",
            description = "Report translation completion, overall and per source file.")
    static final class Coverage implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Coverage.class);

        @Parameters(index = "0", description = "Project JSON.")
        private Path projectFile;

        @Override
        public Integer call() {
            LocalizationProjectService service = new LocalizationProjectService();
            try {
                LocalizationProject project = service.read(projectFile);
                TranslationCoverageReport report =
                        new TranslationCoverageAuditor().audit(project);
                for (FileTranslationCoverage file : report.files()) {
                    LOG.info("{}: {}/{} ({}%)",
                            file.sourceFile(), file.translatedEntries(), file.totalEntries(),
                            Math.round(file.translatedFraction() * 100));
                }
                LOG.info("Overall: {}/{} ({}%)",
                        report.translatedEntries(), report.totalEntries(),
                        Math.round(report.translatedFraction() * 100));
                return 0;
            } catch (ProjectException exception) {
                LOG.error(CliDiagnostics.explain("Coverage report", exception));
                return 1;
            }
        }
    }
}
