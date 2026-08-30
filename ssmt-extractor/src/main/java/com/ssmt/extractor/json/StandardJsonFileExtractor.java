package com.ssmt.extractor.json;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Applies conservative standard schemas to supported Starsector JSON-like files.
 */
public final class StandardJsonFileExtractor implements FileExtractor {

    private static final JsonExtractionSpec FACTION_SPEC =
            JsonExtractionSpec.selected(
                    Set.of(
                            "/displayName",
                            "/displayNameWithArticle",
                            "/displayNameLong",
                            "/displayNameLongWithArticle"),
                    Set.of(
                            "/ranks/ranks/*/name",
                            "/ranks/posts/*/name",
                            "/fleetTypeNames/*"));
    private static final JsonExtractionSpec VARIANT_SPEC =
            JsonExtractionSpec.selectedPointers(Set.of("/displayName"));

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        String normalized = normalize(sourceFile);
        return normalized.equals("data/strings/strings.json")
                || normalized.endsWith("/data/strings/strings.json")
                || normalized.endsWith(".faction")
                || normalized.endsWith(".variant");
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        String relative = normalize(request.relativeSourceFile());
        JsonExtractionSpec spec;
        if (relative.equals("data/strings/strings.json")) {
            spec = JsonExtractionSpec.allTextLeaves();
        } else if (relative.endsWith(".faction")) {
            spec = FACTION_SPEC;
        } else if (relative.endsWith(".variant")) {
            spec = VARIANT_SPEC;
        } else {
            throw new SsmtParseException(
                    "No standard JSON schema for source", request.sourceFile());
        }
        return new JsonExtractor(spec).extract(request);
    }

    private static String normalize(Path path) {
        return path.normalize().toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
    }
}
