package com.ssmt.project;

import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import com.ssmt.scanner.PackageIdentityAudit;
import java.io.IOException;
import java.nio.file.Path;

/** Final candidate/package binding after all referenced reports are complete. */
public final class FinalEvidenceService {
    /** Exact immutable inputs to the final recorded-evidence decision. */
    public record Report(int schemaVersion, String status, String candidateSha256,
            String packageSha256, String assuranceLedgerSha256, String feedbackSha256,
            String archiveRoot, boolean evidenceSemanticsIndependentlyVerified) { }

    /** Audits package identity last and binds it to READY assurance and feedback records. */
    public Report finalizeEvidence(Path candidate, Path archive, String archiveRoot,
            Path ledger, Path feedback) throws IOException {
        var before = new CandidateInventory().capture(candidate);
        String candidateHash = InventoryFingerprint.tree(before.stream().map(entry ->
                new InventoryFingerprint.File(entry.path(), entry.bytes(), entry.sha256())).toList());
        String packageHash = InventoryFingerprint.archive(archive);
        AssuranceSummary.Summary assurance = new AssuranceLedgerReader().read(ledger, candidateHash);
        if (!assurance.status().equals("READY")) {
            throw new IOException("Assurance ledger is not READY: " + assurance.status());
        }
        AttemptFeedbackReader.Checked checkedFeedback =
                new AttemptFeedbackReader().read(feedback, candidateHash);
        if (!checkedFeedback.status().equals("READY_FOR_NEXT_CANDIDATE")) {
            throw new IOException("Attempt feedback is not complete: " + checkedFeedback.status());
        }
        PackageIdentityAudit.Result identity =
                new PackageIdentityAudit().compare(candidate, archive, archiveRoot);
        if (!identity.identical()) {
            throw new IOException("Final candidate and package bytes differ: " + identity);
        }
        if (!before.equals(new CandidateInventory().capture(candidate))
                || !packageHash.equals(InventoryFingerprint.archive(archive))) {
            throw new IOException("Candidate or package changed during final evidence audit");
        }
        return new Report(1, "FINAL_BYTES_AND_RECORDED_EVIDENCE_AGREE", candidateHash,
                packageHash, InventoryFingerprint.archive(ledger),
                InventoryFingerprint.archive(feedback), archiveRoot == null ? "" : archiveRoot,
                false);
    }
}
