package com.ssmt.extractor.csv;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Selects the explicit standard CSV schema for a mod-relative source path.
 */
public final class StandardCsvFileExtractor implements FileExtractor {

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        return StandardCsvSchemas.matchesSuffix(sourceFile);
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        CsvExtractionSpec spec = StandardCsvSchemas.find(request.relativeSourceFile())
                .orElseThrow(() -> new SsmtParseException(
                        "No standard CSV schema for source", request.sourceFile()));
        return new CsvExtractor(spec).extract(request);
    }
}
