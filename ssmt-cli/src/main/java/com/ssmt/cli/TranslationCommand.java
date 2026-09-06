package com.ssmt.cli;

import com.ssmt.project.TranslationWorkflow;
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
    @Parameters(index = "0", description = "Operation: load, export, import, build.")
    private String action;
    @Parameters(index = "1", description = "Source mod folder.")
    private Path source;
    @Parameters(index = "2", arity = "0..1", description = "Export/import JSON or build destination.")
    private Path destination;
    @Option(names = "--workspace", description = "Override the app-owned workspace root.")
    private Path workspace;

    @Override public Integer call() {
        try {
            if (!java.util.Set.of("load", "export", "import", "build").contains(action)) {
                throw new IllegalArgumentException("Operation must be load, export, import, or build");
            }
            if (!action.equals("load") && destination == null) {
                throw new IllegalArgumentException("This operation needs a JSON file or build destination");
            }
            var workflow = workspace == null ? new TranslationWorkflow() : new TranslationWorkflow(workspace);
            var session = workflow.loadMod(source);
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
}
