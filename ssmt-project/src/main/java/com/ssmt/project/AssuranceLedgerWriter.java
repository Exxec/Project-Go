package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the gates one evidence source owns into an assurance ledger. A new ledger starts with every other gate
 * NOT_TESTED; an existing ledger of the same candidate keeps its other results and references. The evidence files
 * themselves are written by the caller beside the ledger.
 */
final class AssuranceLedgerWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private AssuranceLedgerWriter() { }

    /**
     * @param ledgerFile ledger to create or replace atomically
     * @param candidateSha256 candidate fingerprint the ledger binds to
     * @param owned gates this source decides (their previous results are replaced)
     * @param decided the new results for exactly the owned gates
     * @param evidenceHashes evidence path (relative to the ledger) to its SHA-256; only paths a result uses are listed
     */
    static AssuranceLedgerReader.Ledger merge(Path ledgerFile, String candidateSha256, Set<AssuranceSummary.Gate> owned,
            List<AssuranceSummary.Result> decided, Map<String, String> evidenceHashes) throws ProjectException {
        try {
            List<AssuranceSummary.Result> results = new ArrayList<>();
            List<AssuranceLedgerReader.Reference> references = new ArrayList<>();
            if (Files.isRegularFile(ledgerFile)) {
                var existing = JSON.readValue(ledgerFile.toFile(), AssuranceLedgerReader.Ledger.class);
                if (!existing.candidateSha256().equals(candidateSha256)) {
                    throw new ProjectException("The existing ledger belongs to a different candidate");
                }
                existing.results().stream().filter(item -> !owned.contains(item.gate())).forEach(results::add);
                existing.references().stream().filter(reference -> !evidenceHashes.containsKey(reference.path()))
                        .forEach(references::add);
                for (var path : evidenceHashes.keySet()) {
                    if (results.stream().anyMatch(item -> path.equals(item.evidence()))) {
                        throw new ProjectException("Another gate already references " + path);
                    }
                }
            } else {
                Arrays.stream(AssuranceSummary.Gate.values()).filter(gate -> !owned.contains(gate)).forEach(gate ->
                        results.add(new AssuranceSummary.Result(gate, AssuranceSummary.Disposition.NOT_TESTED,
                                candidateSha256, gate.name(), "", "Pending evidence; no completion claim")));
            }
            results.addAll(decided);
            evidenceHashes.forEach((path, hash) -> {
                if (results.stream().anyMatch(item -> path.equals(item.evidence()))) {
                    references.add(new AssuranceLedgerReader.Reference(path, hash));
                }
            });
            var ledger = new AssuranceLedgerReader.Ledger(1, candidateSha256, results, references);
            Path directory = java.util.Objects.requireNonNull(ledgerFile.toAbsolutePath().getParent());
            Files.createDirectories(directory);
            TranslationSnapshot.writeAtomic(ledgerFile.toAbsolutePath(),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(ledger));
            return ledger;
        } catch (IOException exception) {
            throw new ProjectException("Could not write the ledger: " + exception.getMessage(), exception);
        }
    }
}
