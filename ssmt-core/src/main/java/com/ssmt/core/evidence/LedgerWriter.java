package com.ssmt.core.evidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes a new immutable structural snapshot; existing evidence is never replaced. */
public final class LedgerWriter {
    private LedgerWriter() { }

    public static void writeLedger(String ledgerPath, RuntimeEvidence.Ledger ledger) throws IOException {
        SchemaValidator.validateLedger(ledger);
        var candidate = ledger.candidate();
        String json = "{\"candidate\":{\"mod_id\":" + quote(candidate.modId())
                + ",\"candidate_id\":" + quote(candidate.candidateId())
                + ",\"build_hash\":" + quote(candidate.buildHash())
                + "},\"total_bytes\":" + ledger.totalBytes()
                + ",\"entry_count\":" + ledger.entryCount()
                + ",\"structural_gaps\":" + ledger.structuralGaps()
                + ",\"verdict\":" + quote(ledger.verdict())
                + ",\"finding\":" + quote(ledger.finding()) + "}\n";
        Files.writeString(Path.of(ledgerPath), json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }
}
