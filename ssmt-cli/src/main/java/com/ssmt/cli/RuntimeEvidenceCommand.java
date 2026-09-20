package com.ssmt.cli;

import com.ssmt.project.RuntimeEvidenceReader;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Validates machine-readable Starsector launch context without interpreting gameplay semantics. */
@Command(name = "runtime-evidence", mixinStandardHelpOptions = true,
        description = "Check candidate-bound Starsector launch context or emit a pending capture template.")
public final class RuntimeEvidenceCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Runtime evidence JSON, unless --template is used.")
    private Path evidence;
    @Option(names = "--candidate", required = true, description = "Exact selected mod directory, not an archive wrapper.")
    private Path candidate;
    @Option(names = "--template", description = "Emit a pending capture template; write nothing locally.")
    private boolean template;
    @Option(names = "--json", description = "Emit machine-readable verified capture details.")
    private boolean json;
    @Spec private CommandSpec spec;

    @Override public Integer call() {
        try {
            if (template == (evidence != null)) {
                throw new IllegalArgumentException("Supply either a runtime evidence file or --template, not both");
            }
            new com.ssmt.scanner.ModInfoReader().read(candidate);
            var inventory = new CandidateInventory().capture(candidate);
            String hash = InventoryFingerprint.tree(inventory.stream().map(file ->
                    new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (template) {
                spec.commandLine().getOut().println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                        new RuntimeEvidenceReader.Profile(2, hash, "REPLACE_WITH_EXACT_BUILD",
                                List.of(new RuntimeEvidenceReader.EnabledMod(
                                        "REPLACE_WITH_MOD_ID", "REPLACE_WITH_MOD_VERSION")),
                                List.of("REPLACE_WITH_MOD_ID"),
                                "REPLACE_WITH_EXACT_JAVA_EXECUTABLE",
                                "REPLACE_WITH_EXACT_JAVA_VERSION", 0, List.of(), List.of(),
                                runtimeScenarioTemplate())));
                return 0;
            }
            var profile = new RuntimeEvidenceReader().read(evidence, hash);
            if (!inventory.equals(new CandidateInventory().capture(candidate))) {
                throw new java.io.IOException("Candidate changed during runtime evidence inspection");
            }
            if (json) { spec.commandLine().getOut().println(mapper.writeValueAsString(profile)); }
            else {
                spec.commandLine().getOut().println("Runtime capture verified for candidate: " + hash);
                spec.commandLine().getOut().println("Process exit: " + profile.processExitCode());
                spec.commandLine().getOut().println("Scenario semantics NOT_VERIFIED");
            }
            return 0;
        } catch (java.io.IOException | com.ssmt.core.exception.SsmtParseException
                | IllegalArgumentException | java.io.UncheckedIOException exception) {
            spec.commandLine().getErr().println("Runtime evidence inspection failed: " + exception.getMessage());
            return 1;
        }
    }

    private static List<RuntimeEvidenceReader.ScenarioResult> runtimeScenarioTemplate() {
        return List.of(com.ssmt.project.AssuranceSummary.Gate.AUTOMATED_BOOT,
                com.ssmt.project.AssuranceSummary.Gate.CAMPAIGN,
                com.ssmt.project.AssuranceSummary.Gate.COMBAT,
                com.ssmt.project.AssuranceSummary.Gate.SAVE_RELOAD,
                com.ssmt.project.AssuranceSummary.Gate.UPGRADE_COMPATIBILITY).stream()
                .map(gate -> new RuntimeEvidenceReader.ScenarioResult(gate,
                        com.ssmt.project.AssuranceSummary.Disposition.NOT_TESTED,
                        "REPLACE_WITH_" + gate + "_SCENARIO", "Pending live execution"))
                .toList();
    }
}
