package com.ssmt.extractor;

import com.ssmt.core.CancellationToken;
import com.ssmt.core.RuntimeBudgets;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Discovers source files and delegates each supported file to one extractor.
 */
public final class ExtractionCoordinator {

    private static final Comparator<ExtractedString> STRING_ORDER =
            Comparator.comparing((ExtractedString value) ->
                            normalizedPath(value.sourceFile()))
                    .thenComparing(ExtractedString::key);

    private final List<FileExtractor> extractors;

    public ExtractionCoordinator(List<FileExtractor> extractors) {
        Objects.requireNonNull(extractors, "extractors must not be null");
        this.extractors = List.copyOf(extractors);
        if (this.extractors.isEmpty()) {
            throw new IllegalArgumentException("extractors must not be empty");
        }
    }

    /**
     * Recursively extracts one mod without following symbolic links.
     *
     * @param modId owning mod id
     * @param modRoot mod root directory
     * @return deterministic extraction report
     * @throws SsmtParseException for discovery, handler ambiguity, or parse failures
     */
    public ExtractionReport extractMod(String modId, Path modRoot)
            throws SsmtParseException {
        return extractMod(modId, modRoot, CancellationToken.NONE, RuntimeBudgets.DEFAULTS);
    }

    /**
     * Extracts with cooperative cancellation and explicit resource budgets.
     *
     * @param modId owning mod id
     * @param modRoot mod root directory
     * @param cancellation cancellation signal
     * @param budgets bounded-work configuration
     * @return deterministic extraction report
     * @throws SsmtParseException for discovery, budget, ambiguity, or parse failures
     */
    public ExtractionReport extractMod(
            String modId,
            Path modRoot,
            CancellationToken cancellation,
            RuntimeBudgets budgets) throws SsmtParseException {
        Objects.requireNonNull(cancellation, "cancellation must not be null");
        Objects.requireNonNull(budgets, "budgets must not be null");
        cancellation.throwIfCancellationRequested();
        Path normalizedRoot = modRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new SsmtParseException("Not a directory", normalizedRoot);
        }

        List<Path> files = discoverFiles(normalizedRoot, cancellation, budgets);
        List<ExtractedString> strings = new ArrayList<>();
        List<Path> skipped = new ArrayList<>();
        for (Path sourceFile : files) {
            cancellation.throwIfCancellationRequested();
            try {
                if (Files.size(sourceFile) > budgets.maximumInputBytes()) {
                    throw new SsmtParseException(
                            "Source file exceeds configured input budget", sourceFile);
                }
            } catch (IOException exception) {
                throw new SsmtParseException(
                        "Could not inspect source file size", sourceFile, exception);
            }
            List<FileExtractor> matches = extractors.stream()
                    .filter(extractor -> extractor.supports(sourceFile))
                    .toList();
            if (matches.isEmpty()) {
                skipped.add(normalizedRoot.relativize(sourceFile));
            } else if (matches.size() > 1) {
                throw new SsmtParseException(
                        "Multiple extractors support source file", sourceFile);
            } else {
                strings.addAll(matches.getFirst().extract(
                        new ExtractionRequest(modId, normalizedRoot, sourceFile)));
            }
        }

        strings.sort(STRING_ORDER);
        skipped.sort(Comparator.comparing(ExtractionCoordinator::normalizedPath));
        return new ExtractionReport(strings, skipped);
    }

    private static List<Path> discoverFiles(
            Path modRoot,
            CancellationToken cancellation,
            RuntimeBudgets budgets) throws SsmtParseException {
        try (Stream<Path> paths = Files.walk(modRoot)) {
            List<Path> files = new ArrayList<>();
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                cancellation.throwIfCancellationRequested();
                Path path = iterator.next();
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(path);
                    if (files.size() > budgets.maximumSourceFiles()) {
                        throw new SsmtParseException(
                                "Source file count exceeds configured budget", modRoot);
                    }
                }
            }
            files.sort(Comparator.comparing(ExtractionCoordinator::normalizedPath));
            return List.copyOf(files);
        } catch (IOException exception) {
            throw new SsmtParseException("Could not discover mod files", modRoot, exception);
        }
    }

    private static String normalizedPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
