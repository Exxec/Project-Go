package com.ssmt.cli;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModInfo;
import com.ssmt.extractor.CoverageGapAuditor;
import com.ssmt.extractor.CoverageGapFinding;
import com.ssmt.extractor.ExtractionCoordinator;
import com.ssmt.extractor.ExtractionReport;
import com.ssmt.extractor.bytecode.ClassStringExtractor;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import com.ssmt.extractor.text.MissionTextExtractor;
import com.ssmt.scanner.ModInfoReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
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
        try {
            ModInfo mod = modInfoReader.read(modDirectory);
            ExtractionReport report =
                    coordinator.extractMod(mod.id(), mod.sourceDirectory());
            report.skippedFiles().forEach(path ->
                    LOG.warn("Skipped unsupported file: {}", path));
            for (CoverageGapFinding finding
                    : coverageGapAuditor.audit(mod.sourceDirectory(), report)) {
                LOG.warn("Possible missed translatable content in {}: \"{}...\" "
                                + "(no StandardCsvSchemas entry recognizes this file; "
                                + "review and consider --csv-schema or a future schema addition)",
                        finding.relativeSourceFile(), finding.sample());
            }
            LOG.info("Extraction complete: {} string(s), {} unsupported file(s)",
                    report.strings().size(), report.skippedFiles().size());
            return 0;
        } catch (SsmtParseException exception) {
            LOG.error("Extraction failed: {}", exception.getMessage());
            return 1;
        }
    }
}
