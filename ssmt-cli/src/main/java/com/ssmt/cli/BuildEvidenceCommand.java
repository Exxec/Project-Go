package com.ssmt.cli;

import com.ssmt.project.BuildEvidenceReader;
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

/** Inspects exact build context without treating compilation as runtime compatibility. */
@Command(name = "build-evidence", mixinStandardHelpOptions = true,
        description = "Check candidate-bound build evidence or emit a pending capture template.")
public final class BuildEvidenceCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Build evidence JSON, unless --template is used.")
    private Path evidence;
    @Option(names = "--candidate", required = true,
            description = "Exact selected mod directory, not an archive wrapper.")
    private Path candidate;
    @Option(names = "--template", description = "Emit a pending build-evidence template; write nothing locally.")
    private boolean template;
    @Option(names = "--json", description = "Emit machine-readable verified build details.")
    private boolean json;
    @Spec private CommandSpec spec;

    @Override public Integer call() {
        try {
            if (template == (evidence != null)) {
                throw new IllegalArgumentException("Supply either a build evidence file or --template, not both");
            }
            new com.ssmt.scanner.ModInfoReader().read(candidate);
            var before = new CandidateInventory().capture(candidate);
            String hash = InventoryFingerprint.tree(before.stream().map(file ->
                    new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (template) {
                var review = BuildEvidenceReader.AuthorityDisposition.REVIEW_REQUIRED;
                var profile = new BuildEvidenceReader.Profile(1, hash,
                        "REPLACE_WITH_EXACT_JDK_EXECUTABLE", "REPLACE_WITH_EXACT_JDK_VERSION",
                        List.of("REPLACE_WITH_EXECUTABLE", "REPLACE_WITH_ARGUMENTS"),
                        "REPLACE_WITH_WORKING_DIRECTORY", 0,
                        List.of(new BuildEvidenceReader.FileReference(
                                "REPLACE_WITH_BUILD_INPUT", "0".repeat(64))),
                        List.of(), List.of(new BuildEvidenceReader.FileReference(
                                "REPLACE_WITH_BUILD_OUTPUT", "0".repeat(64))),
                        List.of(new BuildEvidenceReader.Authority("SOURCE", review,
                                        "Replace with reviewed source authority"),
                                new BuildEvidenceReader.Authority("COMPILED_JAR", review,
                                        "Replace with reviewed source/JAR relationship"),
                                new BuildEvidenceReader.Authority("LOADER_PROVIDER", review,
                                        "Replace with reviewed loader/provider ownership")));
                spec.commandLine().getOut().println(mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(profile));
                return 0;
            }
            var profile = new BuildEvidenceReader().read(evidence, hash);
            if (!before.equals(new CandidateInventory().capture(candidate))) {
                throw new java.io.IOException("Candidate changed during build evidence inspection");
            }
            if (json) { spec.commandLine().getOut().println(mapper.writeValueAsString(profile)); }
            else {
                spec.commandLine().getOut().println("Build capture verified for candidate: " + hash);
                spec.commandLine().getOut().println("Process exit: " + profile.processExitCode());
                spec.commandLine().getOut().println("Authority dispositions recorded; semantics NOT_VERIFIED");
            }
            return 0;
        } catch (java.io.IOException | com.ssmt.core.exception.SsmtParseException
                | IllegalArgumentException | java.io.UncheckedIOException exception) {
            spec.commandLine().getErr().println("Build evidence inspection failed: " + exception.getMessage());
            return 1;
        }
    }
}
