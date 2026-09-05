package com.ssmt.cli;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModInfo;
import com.ssmt.extractor.CoverageGapAuditor;
import com.ssmt.extractor.CoverageGapFinding;
import com.ssmt.extractor.CsvGapSchemaSuggester;
import com.ssmt.extractor.ExtractionCoordinator;
import com.ssmt.extractor.ExtractionReport;
import com.ssmt.extractor.GapSchemaStatus;
import com.ssmt.extractor.GapSchemaSuggestion;
import com.ssmt.extractor.bytecode.ClassStringExtractor;
import com.ssmt.extractor.csv.OptInCsvFileSchema;
import com.ssmt.extractor.csv.OptInCsvSchema;
import com.ssmt.extractor.csv.OptInCsvSchemaCatalog;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import com.ssmt.extractor.text.MissionTextExtractor;
import com.ssmt.scanner.ModInfoReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Implements {@code ssmt extract MOD_DIRECTORY}.
 */
@Command(
        name = "extract",
        description = "Extract strings from supported files in one Starsector mod."
)
public final class ExtractCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractCommand.class);

    @Parameters(index = "0", description = "Starsector mod root directory.")
    private Path modDirectory;

    @Option(
            names = "--suggest-csv-schema",
            description = "Write a reviewable draft opt-in CSV schema catalog for coverage gaps.")
    private Optional<Path> suggestCsvSchema = Optional.empty();

    @Option(
            names = "--merge-into",
            description = "Merge suggestions into an existing catalog "
                    + "(requires --suggest-csv-schema).")
    private Optional<Path> mergeInto = Optional.empty();

    private final ModInfoReader modInfoReader;
    private final ExtractionCoordinator coordinator;
    private final CoverageGapAuditor coverageGapAuditor;

    public ExtractCommand() {
        this(
                new ModInfoReader(),
                new ExtractionCoordinator(List.of(
                        new StandardCsvFileExtractor(),
                        new StandardJsonFileExtractor(),
                        new ClassStringExtractor(),
                        new MissionTextExtractor())),
                new CoverageGapAuditor());
    }

    ExtractCommand(
            ModInfoReader modInfoReader,
            ExtractionCoordinator coordinator,
            CoverageGapAuditor coverageGapAuditor) {
        this.modInfoReader = modInfoReader;
        this.coordinator = coordinator;
        this.coverageGapAuditor = coverageGapAuditor;
    }

    @Override
    public Integer call() {
        if (mergeInto.isPresent() && suggestCsvSchema.isEmpty()) {
            LOG.error("--merge-into requires --suggest-csv-schema");
            return ExitCode.USAGE;
        }
        try {
            ModInfo mod = modInfoReader.read(modDirectory);
            ExtractionReport report =
                    coordinator.extractMod(mod.id(), mod.sourceDirectory());
            report.skippedFiles().forEach(path ->
                    LOG.warn("Skipped unsupported file: {}", path));
            List<CoverageGapFinding> findings =
                    coverageGapAuditor.audit(mod.sourceDirectory(), report);
            for (CoverageGapFinding finding : findings) {
                LOG.warn("Possible missed translatable content in {}: \"{}...\" "
                                + "(no StandardCsvSchemas entry recognizes this file; "
                                + "review and consider --suggest-csv-schema, --csv-schema, "
                                + "or a future schema addition)",
                        finding.relativeSourceFile(), finding.sample());
            }
            LOG.info("Extraction complete: {} string(s), {} unsupported file(s)",
                    report.strings().size(), report.skippedFiles().size());
            return suggestCsvSchema.isPresent()
                    ? writeSuggestions(mod.sourceDirectory(), findings)
                    : 0;
        } catch (SsmtParseException exception) {
            LOG.error("Extraction failed: {}", exception.getMessage());
            return 1;
        }
    }

    private Integer writeSuggestions(Path modRoot, List<CoverageGapFinding> findings) {
        Path destination = suggestCsvSchema.orElseThrow();
        try {
            List<GapSchemaSuggestion> suggestions =
                    new CsvGapSchemaSuggester().suggest(modRoot, findings);
            for (GapSchemaSuggestion suggestion : suggestions) {
                if (suggestion.status() == GapSchemaStatus.SUGGESTED) {
                    OptInCsvFileSchema schema = suggestion.schema().orElseThrow();
                    LOG.info("Suggested CSV schema for {}: identity={}, textColumns={}",
                            suggestion.relativeSourceFile(),
                            schema.identityColumns(),
                            schema.textColumns());
                } else {
                    LOG.warn("No CSV schema suggested for {} ({}): {}",
                            suggestion.relativeSourceFile(),
                            suggestion.status(),
                            suggestion.reason());
                }
            }
            OptInCsvSchema catalog;
            if (mergeInto.isPresent()) {
                OptInCsvSchema existing = new OptInCsvSchemaCatalog().read(mergeInto.get());
                catalog = CsvGapSchemaSuggester.mergeInto(existing, suggestions);
            } else {
                catalog = CsvGapSchemaSuggester.toCatalog(suggestions);
            }
            new OptInCsvSchemaCatalog().write(destination, catalog);
            LOG.info("Wrote {} suggested CSV schema file(s) to {}; review, edit if needed, "
                            + "then pass via --csv-schema",
                    catalog.files().size(), destination);
            return 0;
        } catch (SsmtParseException exception) {
            LOG.error("CSV schema suggestion failed: {}", exception.getMessage());
            return 1;
        } catch (IllegalArgumentException exception) {
            LOG.error("CSV schema suggestion rejected: {}", exception.getMessage());
            return 1;
        }
    }
}
