package com.ssmt.cli;

import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.LegacyProjectCandidate;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** The same four normal operations as the desktop, without TM or provider setup. */
@Command(name = "translation", mixinStandardHelpOptions = true,
        description = "Choose a mod, export/import one translation JSON, or build a translated copy.")
public final class TranslationCommand implements Callable<Integer> {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TranslationCommand.class);
    @Parameters(index = "0", description = "Operation: load, export, import, build, legacy.")
    private String action;
    @Parameters(index = "1", description = "Mod ZIP, folder, or mod_info.json.")
    private Path source;
    @Parameters(index = "2", arity = "0..1", description = "Export/import JSON or build destination.")
    private Path destination;
    @Option(names = "--workspace", description = "Override the app-owned workspace root.")
    private Path workspace;
    @Option(names = "--adopt-legacy", description = "Explicit old .ssmt.json file to copy.")
    private Path adoptLegacy;
    @Option(names = "--start-fresh", description = "Ignore listed old projects and start clean.")
    private boolean startFresh;
    @Option(names = "--use-previous", description = "Resolve a same-id conflict using the previous translation work.")
    private boolean usePrevious;
    @Option(names = "--start-separately", description = "Resolve a same-id conflict with separate translation work.")
    private boolean startSeparately;

    @Override public Integer call() {
        try {
            if (!java.util.Set.of("load", "export", "import", "build", "legacy").contains(action)) {
                throw new IllegalArgumentException("Operation must be load, export, import, build, or legacy");
            }
            if (!action.equals("load") && !action.equals("legacy") && destination == null) {
                throw new IllegalArgumentException("This operation needs a JSON file or build destination");
            }
            if (startFresh && adoptLegacy != null) {
                throw new IllegalArgumentException("Choose either --start-fresh or --adopt-legacy");
            }
            if (usePrevious && startSeparately) {
                throw new IllegalArgumentException("Choose either --use-previous or --start-separately");
            }
            if (adoptLegacy != null && (usePrevious || startSeparately)) {
                throw new IllegalArgumentException("Legacy adoption and lineage choices are separate operations");
            }
            var workflow = workspace == null ? new TranslationWorkflow() : new TranslationWorkflow(workspace);
            var legacy = workflow.legacyProjects(source);
            if (action.equals("legacy")) {
                logLegacy(legacy);
                return 0;
            }
            TranslationWorkflow.Session session;
            if (adoptLegacy != null) {
                Path selected;
                try {
                    selected = adoptLegacy.toRealPath();
                } catch (java.io.IOException exception) {
                    throw new IllegalArgumentException("Could not open the selected old project", exception);
                }
                LegacyProjectCandidate candidate = legacy.stream()
                        .filter(item -> item.file().equals(selected)).findFirst().orElseThrow(() ->
                                new IllegalArgumentException(
                                        "The selected old project is not in the eligible legacy list"));
                session = workflow.adoptLegacy(source, candidate);
            } else if (!legacy.isEmpty() && !startFresh) {
                logLegacy(legacy);
                throw new IllegalArgumentException(
                        "Old translation work needs an explicit --adopt-legacy FILE or --start-fresh choice");
            } else {
                TranslationWorkflow.LineageChoice choice = usePrevious
                        ? TranslationWorkflow.LineageChoice.USE_PREVIOUS
                        : startSeparately ? TranslationWorkflow.LineageChoice.START_SEPARATELY : null;
                session = workflow.loadInput(source, choice);
            }
            switch (action) {
                case "export" -> workflow.exportTranslation(session, destination);
                case "import" -> session = workflow.importTranslation(session, destination);
                case "build" -> workflow.buildPatch(session, destination);
                default -> { /* load is already complete */ }
            }
            long translated = session.project().entries().stream().filter(e -> !e.translatedText().isBlank()).count();
            LOG.info(session.modName() + ": " + translated + "/" + session.project().entries().size()
                    + " translated; " + session.needsReview() + " changed texts need translation");
            return 0;
        } catch (com.ssmt.project.ProjectException | IllegalArgumentException exception) {
            LOG.error(exception.getMessage());
            return 1;
        }
    }

    private static void logLegacy(java.util.List<LegacyProjectCandidate> candidates) {
        if (candidates.isEmpty()) {
            LOG.info("No eligible old translation projects were found");
            return;
        }
        for (LegacyProjectCandidate candidate : candidates) {
            LOG.info("Old project: " + candidate.file() + " | "
                    + candidate.translatedEntries() + "/" + candidate.entries()
                    + " translated | SHA-256 " + candidate.sha256());
        }
    }
}
