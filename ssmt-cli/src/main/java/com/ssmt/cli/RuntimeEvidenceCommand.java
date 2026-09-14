package com.ssmt.cli;

import com.ssmt.project.RuntimeEvidenceReader;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
                        Map.of("schemaVersion", 1, "candidateSha256", hash,
                                "starsectorBuild", "REPLACE_WITH_EXACT_BUILD",
                                "enabledMods", List.of(Map.of("id", "REPLACE_WITH_MOD_ID", "version", "REPLACE_WITH_MOD_VERSION")),
                                "loadOrder", List.of("REPLACE_WITH_MOD_ID"),
                                "javaExecutable", "REPLACE_WITH_EXACT_JAVA_EXECUTABLE",
                                "javaVersion", "REPLACE_WITH_EXACT_JAVA_VERSION", "processExitCode", 0,
                                "logs", List.of(), "modalDialogs", List.of())));
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
}
