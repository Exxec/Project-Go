package com.ssmt.core.plugin;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import java.nio.file.Path;
import java.util.List;

/**
 * Format-neutral contract implemented by localizable file handlers.
 */
public interface FileExtractor {

    /**
     * Determines whether this extractor handles a source path.
     *
     * @param sourceFile candidate source path
     * @return whether this extractor supports the path
     */
    boolean supports(Path sourceFile);

    /**
     * Extracts exact source strings in deterministic order without writing to
     * the source file or mod root.
     *
     * @param request normalized extraction request
     * @return immutable extracted strings
     * @throws SsmtParseException when the source cannot be read or parsed
     */
    List<ExtractedString> extract(ExtractionRequest request) throws SsmtParseException;
}
