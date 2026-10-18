package com.ssmt.cli;

import com.ssmt.project.AssuranceSummary;
import com.ssmt.project.CandidateArchiveAudit;
import com.ssmt.project.ProjectException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Final candidate ZIP audit: PACKAGE_IDENTITY and STATIC_VALIDATION evidence for an assurance ledger. */
@Command(name = "audit-candidate", mixinStandardHelpOptions = true,
        description = "Extract a candidate ZIP safely, compare every file hash with the publication manifest, "
                + "run the leftover check and write the PACKAGE_IDENTITY and STATIC_VALIDATION gates of a ledger.")
public final class AuditCandidateCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(AuditCandidateCommand.class);
    @Parameters(index = "0", description = "Candidate ZIP archive.")
    private Path archive;
    @Option(names = "--manifest", required = true,
            description = "Publication manifest (installed-copy.json of the workspace) written when the copy was built.")
    private Path manifest;
    @Option(names = "--ledger", required = true,
            description = "Assurance ledger to write (an existing ledger of the same candidate is merged); "
                    + "candidate-audit.json is written beside it.")
    private Path ledger;
    @Option(names = "--work-dir", description = "Empty directory for the bounded extraction (default: a temporary one).")
    private Path workDirectory;
    @Option(names = "--expect-id", description = "Required mod id.")
    private String expectedId;
    @Option(names = "--expect-version", description = "Required mod version.")
    private String expectedVersion;

    @Override public Integer call() {
        try {
            Path work = workDirectory != null ? workDirectory : Files.createTempDirectory("ssmt-candidate-audit-");
            var audit = new CandidateArchiveAudit();
            var result = audit.audit(archive, manifest, work, Optional.ofNullable(expectedId),
                    Optional.ofNullable(expectedVersion));
            audit.writeLedger(result, ledger);
            LOG.info("PACKAGE_IDENTITY {}: {}", result.packageIdentity(), result.packageReason());
            LOG.info("STATIC_VALIDATION {}: {}", result.staticValidation(), result.staticReason());
            LOG.info("Candidate fingerprint {}; ledger {}", result.candidateSha256(), ledger);
            if (result.packageIdentity() == AssuranceSummary.Disposition.FAIL) {
                return 1;
            }
            return result.staticValidation() == AssuranceSummary.Disposition.PASS ? 0 : 2;
        } catch (ProjectException | IOException exception) {
            LOG.error("Candidate audit failed: {}", exception.getMessage());
            return 1;
        }
    }
}
