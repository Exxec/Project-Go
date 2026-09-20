package com.ssmt.cli;

import com.ssmt.project.AttemptFeedbackReader;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Checks classified feedback without inferring that all real-world surprises were recorded. */
@Command(name = "attempt-feedback", mixinStandardHelpOptions = true,
        description = "Check candidate-bound revival feedback or emit a pending template.")
public final class AttemptFeedbackCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Feedback JSON, unless --template is used.")
    private Path feedback;
    @Option(names = "--candidate", required = true, description = "Exact selected mod directory.")
    private Path candidate;
    @Option(names = "--template", description = "Emit a review-pending feedback template.")
    private boolean template;
    @Option(names = "--json", description = "Emit machine-readable checked feedback.")
    private boolean json;
    @Spec private CommandSpec spec;

    @Override public Integer call() {
        try {
            if (template == (feedback != null)) {
                throw new IllegalArgumentException("Supply either a feedback file or --template, not both");
            }
            new com.ssmt.scanner.ModInfoReader().read(candidate);
            var before = new CandidateInventory().capture(candidate);
            String hash = InventoryFingerprint.tree(before.stream().map(entry ->
                    new InventoryFingerprint.File(entry.path(), entry.bytes(), entry.sha256())).toList());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (template) {
                var record = new AttemptFeedbackReader.Record(1, hash,
                        "REPLACE_WITH_ATTEMPT_ID", false, List.of(), List.of());
                spec.commandLine().getOut().println(mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(record));
                return 0;
            }
            var checked = new AttemptFeedbackReader().read(feedback, hash);
            if (!before.equals(new CandidateInventory().capture(candidate))) {
                throw new java.io.IOException("Candidate changed during feedback inspection");
            }
            if (json) { spec.commandLine().getOut().println(mapper.writeValueAsString(checked)); }
            else { spec.commandLine().getOut().println("Feedback status: " + checked.status()); }
            return checked.status().equals("READY_FOR_NEXT_CANDIDATE") ? 0 : 1;
        } catch (java.io.IOException | com.ssmt.core.exception.SsmtParseException
                | IllegalArgumentException | java.io.UncheckedIOException exception) {
            spec.commandLine().getErr().println("Feedback inspection failed: " + exception.getMessage());
            return 1;
        }
    }
}
