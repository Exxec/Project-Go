package com.ssmt.extractor;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.plugin.FileExtractor;
import java.nio.file.Path;
import java.util.List;

/** Read-only extraction facade. Candidate audits belong to the project module. */
public final class AuditWorkflow {
    private final ExtractionCoordinator coordinator;

    public AuditWorkflow(List<FileExtractor> extractors) {
        coordinator = new ExtractionCoordinator(extractors);
    }

    public ExtractionReport extractMod(String modId, Path modRoot) throws SsmtParseException {
        return coordinator.extractMod(modId, modRoot);
    }
}
