package com.ssmt.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.GlossaryInterchangeService;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectException;
import com.ssmt.project.RootStringDiffService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Compares the translatable strings of two game or mod roots and reports what an old translation still covers. */
@Command(name = "string-diff",
        description = "Compare the translatable strings of two game or mod roots (ADDED/REMOVED/CHANGED/UNCHANGED) and,"
                + " given a pack or glossary for the old root, which translations carry over, are stale or are missing.")
final class StringDiffCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(StringDiffCommand.class);

    @Parameters(index = "0", description = "Old game or mod root (read only).")
    private Path oldRoot;

    @Parameters(index = "1", description = "New game or mod root (read only).")
    private Path newRoot;

    @Option(names = "--pack", description = "Project JSON holding translations of the old root's strings (read only).")
    private Path pack;

    @Option(names = "--glossary", description = "Glossary JSON; a term applies to a string whose whole old text is the"
            + " term source (read only).")
    private Path glossary;

    @Option(names = "--report", required = true, description = "JSON report path (identities and digests only, no"
            + " text); a .txt count summary is written beside it; both must not exist.")
    private Path report;

    @Override
    public Integer call() {
        try {
            Path summaryFile = report.resolveSibling(report.getFileName() + ".txt");
            for (Path target : new Path[] {report, summaryFile}) {
                if (Files.exists(target)) {
                    throw new IllegalArgumentException("Refusing to overwrite " + target);
                }
            }
            RootStringDiffService.Translations translations = null;
            if (pack != null || glossary != null) {
                translations = new RootStringDiffService.Translations();
                if (pack != null) {
                    translations.withProject(new LocalizationProjectService().read(pack));
                }
                if (glossary != null) {
                    translations.withGlossary(new GlossaryInterchangeService().read(glossary));
                }
            }
            var result = new RootStringDiffService().diff(oldRoot, newRoot, translations);
            String summary = RootStringDiffService.summary(result);
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(report.toFile(), RootStringDiffService.toJson(result));
            Files.writeString(summaryFile, summary, StandardCharsets.UTF_8);
            LOG.info(summary);
            return 0;
        } catch (ProjectException | IllegalArgumentException | IOException exception) {
            LOG.error(CliDiagnostics.explain("String comparison", exception));
            return 1;
        }
    }
}
