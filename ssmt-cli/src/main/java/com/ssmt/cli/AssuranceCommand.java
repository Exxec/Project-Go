package com.ssmt.cli;

import com.ssmt.project.AssuranceLedgerReader;
import com.ssmt.project.AssuranceSummary;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Candidate-bound ledger inspection; never certifies arbitrary log contents. */
@Command(name = "assurance", mixinStandardHelpOptions = true,
        description = "Check candidate-bound assurance evidence or emit a pending ledger template.")
public final class AssuranceCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Evidence ledger JSON, unless --template is used.")
    private Path ledger;
    @Option(names = "--candidate", required = true, description = "Exact selected mod directory, not an archive wrapper.")
    private Path candidate;
    @Option(names = "--template", description = "Emit a ledger with every gate NOT_TESTED; write nothing locally.")
    private boolean template;
    @Option(names = "--json", description = "Emit machine-readable checked results.")
    private boolean json;
    @Spec private CommandSpec spec;

    /** Structural/hash checks do not interpret scenario semantics. */
    public record Checked(AssuranceSummary.Summary recordedSummary, boolean evidenceSemanticsVerified) { }

    @Override public Integer call() {
        try {
            if (template == (ledger != null)) {
                throw new IllegalArgumentException("Supply either a ledger or --template, not both");
            }
            new com.ssmt.scanner.ModInfoReader().read(candidate);
            var before = new CandidateInventory().capture(candidate);
            String hash = InventoryFingerprint.tree(before.stream().map(file ->
                    new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (template) {
                var results = Arrays.stream(AssuranceSummary.Gate.values()).map(gate ->
                        new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.NOT_TESTED,
                                hash, gate.name(), "", "Pending evidence; no completion claim")).toList();
                spec.commandLine().getOut().println(mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(new AssuranceLedgerReader.Ledger(1, hash, results, List.of())));
                return 0;
            }
            var summary = new AssuranceLedgerReader().read(ledger, hash);
            if (!before.equals(new CandidateInventory().capture(candidate))) {
                throw new java.io.IOException("Candidate changed during evidence inspection");
            }
            if (json) {
                spec.commandLine().getOut().println(mapper.writeValueAsString(new Checked(summary, false)));
            } else {
                spec.commandLine().getOut().println("Recorded status: " + summary.status());
                spec.commandLine().getOut().println("Evidence hashes verified; scenario semantics NOT_VERIFIED");
                summary.results().forEach(spec.commandLine().getOut()::println);
            }
            return summary.status().equals("READY") ? 0 : 1;
        } catch (java.io.IOException | com.ssmt.core.exception.SsmtParseException
                | IllegalArgumentException | java.io.UncheckedIOException exception) {
            spec.commandLine().getErr().println("Assurance inspection failed: " + exception.getMessage());
            return 1;
        }
    }
}
