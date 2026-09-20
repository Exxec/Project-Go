package com.ssmt.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.FinalEvidenceService;
import com.ssmt.scanner.InventoryFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Writes the final package/report binding without publishing or promoting a release. */
@Command(name = "finalize-evidence", mixinStandardHelpOptions = true,
        description = "Bind an exact candidate/package pair to completed assurance and feedback records.")
public final class FinalizeEvidenceCommand implements Callable<Integer> {
    @Option(names = "--candidate", required = true) private Path candidate;
    @Option(names = "--package", required = true) private Path archive;
    @Option(names = "--ledger", required = true) private Path ledger;
    @Option(names = "--feedback", required = true) private Path feedback;
    @Option(names = "--archive-root", defaultValue = "") private String archiveRoot;
    @Option(names = "--output", required = true, description = "Final JSON report to archive with release hashes.")
    private Path output;
    @Spec private CommandSpec spec;

    @Override public Integer call() {
        Path staged = null;
        try {
            new com.ssmt.scanner.ModInfoReader().read(candidate);
            Path source = candidate.toRealPath();
            Path requested = output.toAbsolutePath().normalize();
            Path parent = Objects.requireNonNull(requested.getParent(), "output parent");
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(parent) || !parent.toRealPath().equals(parent)) {
                throw new IOException("Final report parent must be a canonical directory");
            }
            Path target = parent.toRealPath().resolve(
                    Objects.requireNonNull(requested.getFileName(), "output filename"));
            if (target.startsWith(source)) {
                throw new IllegalArgumentException("Final report must remain outside the candidate");
            }
            if (Files.exists(target)) {
                throw new IllegalArgumentException("Final report output already exists");
            }
            if (target.equals(archive.toRealPath()) || target.equals(ledger.toRealPath())
                    || target.equals(feedback.toRealPath())) {
                throw new IllegalArgumentException("Final report must not replace an evidence input");
            }
            var report = new FinalEvidenceService().finalizeEvidence(
                    candidate, archive, archiveRoot, ledger, feedback);
            staged = Files.createTempFile(parent, ".final-evidence-", ".json");
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), report);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staged, target);
            }
            spec.commandLine().getOut().println("Final evidence: " + target);
            spec.commandLine().getOut().println("Final evidence SHA-256: "
                    + InventoryFingerprint.archive(target));
            spec.commandLine().getOut().println("Evidence semantics independently verified: false");
            return 0;
        } catch (IOException | com.ssmt.core.exception.SsmtParseException
                | IllegalArgumentException | java.io.UncheckedIOException exception) {
            spec.commandLine().getErr().println("Final evidence failed: " + exception.getMessage());
            return 1;
        } finally {
            if (staged != null) {
                try { Files.deleteIfExists(staged); }
                catch (IOException ignored) { /* Report publication already reports the primary failure. */ }
            }
        }
    }
}
