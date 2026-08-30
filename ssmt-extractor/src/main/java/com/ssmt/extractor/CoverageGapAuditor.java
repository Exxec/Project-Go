package com.ssmt.extractor;

import com.ssmt.core.exception.SsmtParseException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only scan of a mod's unrecognized CSV files for likely non-English text, so a
 * missing {@code StandardCsvSchemas} entry surfaces as a reviewable finding instead of
 * shipping untranslated (as happened with {@code data/shipsystems/ship_systems.csv} until
 * BUG-011). Never extracts, translates, approves, or changes any file — findings are
 * advisory only, matching {@code ADR-032}'s evidence-gated coverage policy: expanding
 * standard coverage still requires a human to confirm the field is genuinely
 * player-visible before it's added to {@code StandardCsvSchemas} or an opt-in schema.
 */
public final class CoverageGapAuditor {
    private static final Pattern NON_ASCII_RUN = Pattern.compile("[^\\x00-\\x7F]{2,}");
    private static final int SAMPLE_RADIUS = 40;

    /**
     * Scans every {@code .csv} file an {@link ExtractionCoordinator} run left unsupported.
     *
     * @param modRoot mod root the report was generated from
     * @param report prior extraction result for the same mod
     * @return findings in deterministic path order
     * @throws SsmtParseException when a candidate file cannot be read
     */
    public List<CoverageGapFinding> audit(Path modRoot, ExtractionReport report)
            throws SsmtParseException {
        Path normalizedRoot = modRoot.toAbsolutePath().normalize();
        List<CoverageGapFinding> findings = new ArrayList<>();
        for (Path relative : report.skippedFiles()) {
            String fileName = String.valueOf(relative.getFileName()).toLowerCase(Locale.ROOT);
            if (!fileName.endsWith(".csv")) {
                continue;
            }
            String text = decode(normalizedRoot.resolve(relative));
            Matcher matcher = NON_ASCII_RUN.matcher(text);
            if (matcher.find()) {
                findings.add(new CoverageGapFinding(relative, sample(text, matcher.start())));
            }
        }
        findings.sort(Comparator.comparing(finding -> finding.relativeSourceFile().toString()));
        return List.copyOf(findings);
    }

    private static String sample(String text, int matchStart) {
        int from = Math.max(0, matchStart - SAMPLE_RADIUS);
        int to = Math.min(text.length(), matchStart + SAMPLE_RADIUS);
        return text.substring(from, to)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .strip();
    }

    private static String decode(Path file) throws SsmtParseException {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return decodeStrict(bytes, StandardCharsets.UTF_8);
            } catch (CharacterCodingException invalidUtf8) {
                return decodeStrict(bytes, Charset.forName("GB18030"));
            }
        } catch (IOException exception) {
            throw new SsmtParseException("Could not read candidate file", file, exception);
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }
}
