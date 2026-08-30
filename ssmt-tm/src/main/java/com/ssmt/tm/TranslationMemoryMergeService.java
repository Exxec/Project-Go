package com.ssmt.tm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares and conservatively merges SQLite translation catalogs.
 *
 * <p>Missing identities and strictly higher-preference translations may be
 * merged. Equal/lower-preference disagreements are reported as conflicts and
 * never overwritten automatically.
 */
public final class TranslationMemoryMergeService {

    /**
     * Compares without changing either database.
     */
    public TranslationMemoryMergeResult compare(Path source, Path destination)
            throws TranslationMemoryException {
        return plan(source, destination).result();
    }

    /**
     * Applies only the safe changes reported by {@link #compare(Path, Path)}.
     */
    public TranslationMemoryMergeResult merge(Path source, Path destination)
            throws TranslationMemoryException {
        MergePlan plan = plan(source, destination);
        if (!plan.drafts().isEmpty()) {
            try (SqliteTranslationMemory target =
                    SqliteTranslationMemory.open(destination)) {
                target.upsertAll(plan.drafts());
            }
        }
        return plan.result();
    }

    private static MergePlan plan(Path source, Path destination)
            throws TranslationMemoryException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        if (normalizedSource.equals(normalizedDestination)) {
            throw new TranslationMemoryException(
                    "Source and destination translation memories are the same file");
        }
        if (!Files.isRegularFile(normalizedSource)) {
            throw new TranslationMemoryException(
                    "Source translation memory does not exist: " + normalizedSource);
        }
        if (!Files.isRegularFile(normalizedDestination)) {
            throw new TranslationMemoryException(
                    "Destination translation memory does not exist: " + normalizedDestination);
        }
        List<TranslationEntry> sourceEntries;
        List<TranslationEntry> destinationEntries;
        Path inspectionCopy = null;
        try {
            inspectionCopy = Files.createTempFile("ssmt-memory-compare-", ".db");
            Files.copy(
                    normalizedSource,
                    inspectionCopy,
                    StandardCopyOption.REPLACE_EXISTING);
            try (SqliteTranslationMemory sourceMemory =
                            SqliteTranslationMemory.open(inspectionCopy);
                    SqliteTranslationMemory destinationMemory =
                            SqliteTranslationMemory.open(normalizedDestination)) {
                sourceEntries = sourceMemory.findAll();
                destinationEntries = destinationMemory.findAll();
            }
        } catch (IOException exception) {
            throw new TranslationMemoryException(
                    "Could not create read-only catalog inspection copy", exception);
        } finally {
            if (inspectionCopy != null) {
                try {
                    Files.deleteIfExists(inspectionCopy);
                } catch (IOException ignored) {
                    inspectionCopy.toFile().deleteOnExit();
                }
            }
        }
        Map<Identity, TranslationEntry> existing = new LinkedHashMap<>();
        for (TranslationEntry entry : destinationEntries) {
            existing.put(Identity.of(entry), entry);
        }
        List<TranslationDraft> drafts = new ArrayList<>();
        int added = 0;
        int upgraded = 0;
        int identical = 0;
        int conflicts = 0;
        for (TranslationEntry incoming : sourceEntries) {
            TranslationEntry current = existing.get(Identity.of(incoming));
            if (current == null) {
                drafts.add(draft(incoming));
                added++;
            } else if (current.translatedText().equals(incoming.translatedText())) {
                identical++;
            } else if (incoming.provenance().preferredOver(current.provenance())) {
                drafts.add(draft(incoming));
                upgraded++;
            } else {
                conflicts++;
            }
        }
        return new MergePlan(
                List.copyOf(drafts),
                new TranslationMemoryMergeResult(
                        sourceEntries.size(), added, upgraded, identical, conflicts));
    }

    private static TranslationDraft draft(TranslationEntry entry) {
        return new TranslationDraft(
                entry.sourceText(),
                entry.sourceLanguage(),
                entry.targetLanguage(),
                entry.translatedText(),
                entry.context(),
                entry.provenance());
    }

    private record Identity(
            String sourceText,
            String sourceLanguage,
            String targetLanguage,
            String context) {
        private static Identity of(TranslationEntry entry) {
            return new Identity(
                    entry.sourceText(),
                    entry.sourceLanguage(),
                    entry.targetLanguage(),
                    entry.context());
        }
    }

    private record MergePlan(
            List<TranslationDraft> drafts,
            TranslationMemoryMergeResult result) {
    }
}
