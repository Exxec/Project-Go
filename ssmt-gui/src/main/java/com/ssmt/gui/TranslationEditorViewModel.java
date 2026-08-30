package com.ssmt.gui;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.validation.TranslationValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Side-by-side translation editor state and validation behavior.
 */
public final class TranslationEditorViewModel {
    private final TranslationValidator validator = new TranslationValidator();
    private final Map<TranslationRowId, TranslationRow> rows = new LinkedHashMap<>();
    private final Map<TranslationRowId, List<String>> suggestions = new LinkedHashMap<>();
    private final Set<TranslationRowId> reviewed = new HashSet<>();
    private boolean dirty;

    /**
     * Replaces editor contents with deterministically ordered extracted strings.
     *
     * @param extracted source strings
     */
    public void load(List<ExtractedString> extracted) {
        List<ExtractedString> ordered = new ArrayList<>(List.copyOf(extracted));
        ordered.sort(Comparator
                .comparing((ExtractedString item) -> item.sourceFile().toString())
                .thenComparing(ExtractedString::key));
        Map<TranslationRowId, TranslationRow> replacement = new LinkedHashMap<>();
        for (ExtractedString item : ordered) {
            TranslationRowId id = new TranslationRowId(item.sourceFile(), item.key());
            TranslationRow previous =
                    replacement.put(id, new TranslationRow(id, item.originalText(), "", List.of()));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate extracted row " + id);
            }
        }
        rows.clear();
        rows.putAll(replacement);
        suggestions.clear();
        reviewed.clear();
        dirty = false;
    }

    /**
     * Updates and immediately validates one draft.
     *
     * @param id row id
     * @param translatedText current draft
     */
    public void edit(TranslationRowId id, String translatedText) {
        if (translatedText == null) {
            throw new IllegalArgumentException("translatedText must not be null");
        }
        TranslationRow existing = rows.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown translation row " + id);
        }
        rows.put(id, new TranslationRow(
                id,
                existing.sourceText(),
                translatedText,
                validator.validate(existing.sourceText(), translatedText)));
        dirty = true;
    }

    /**
     * @return immutable rows in source-file/key order
     */
    public List<TranslationRow> rows() {
        return List.copyOf(rows.values());
    }

    /**
     * Finds one row.
     *
     * @param id row id
     * @return row when loaded
     */
    public Optional<TranslationRow> row(TranslationRowId id) {
        return Optional.ofNullable(rows.get(id));
    }

    /**
     * @return whether any current draft has validation findings
     */
    public boolean hasErrors() {
        return rows.values().stream().anyMatch(row -> !row.issues().isEmpty());
    }

    /**
     * Searches current rows without changing their deterministic backing order.
     *
     * @param query case-insensitive source, translation, path, or key query
     * @param status optional review-state restriction
     * @return matching immutable rows
     */
    public List<TranslationRow> filter(
            String query,
            Optional<TranslationStatus> requestedStatus) {
        String needle = query == null
                ? ""
                : query.toLowerCase(java.util.Locale.ROOT);
        Optional<TranslationStatus> requested =
                requestedStatus == null ? Optional.empty() : requestedStatus;
        return rows.values().stream()
                .filter(row -> requested.isEmpty()
                        || requested.orElseThrow() == status(row))
                .filter(row -> needle.isEmpty()
                        || searchableText(row).contains(needle))
                .toList();
    }

    /**
     * @return whether editor state changed since load or the last save
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Marks current drafts as saved.
     */
    public void markSaved() {
        dirty = false;
    }

    /**
     * Replaces advisory suggestions for one row without applying them.
     *
     * @param id row identity
     * @param values exact/fuzzy suggestions
     */
    public void setSuggestions(TranslationRowId id, List<String> values) {
        if (!rows.containsKey(id)) {
            throw new IllegalArgumentException("Unknown translation row " + id);
        }
        suggestions.put(id, List.copyOf(values).stream().distinct().toList());
    }

    /**
     * @param id row identity
     * @return immutable advisory suggestions
     */
    public List<String> suggestions(TranslationRowId id) {
        return List.copyOf(suggestions.getOrDefault(id, List.of()));
    }

    /**
     * Explicitly applies one selected suggestion.
     *
     * @param id row identity
     * @param index selected suggestion index
     */
    public void applySuggestion(TranslationRowId id, int index) {
        edit(id, suggestions(id).get(index));
    }

    /**
     * Marks selected rows reviewed in one explicit bulk action.
     *
     * @param ids selected row identities
     */
    public void markReviewed(List<TranslationRowId> ids) {
        for (TranslationRowId id : List.copyOf(ids)) {
            if (!rows.containsKey(id)) {
                throw new IllegalArgumentException("Unknown translation row " + id);
            }
        }
        reviewed.addAll(ids);
    }

    /**
     * @param id row identity
     * @return whether the row is marked reviewed
     */
    public boolean isReviewed(TranslationRowId id) {
        return reviewed.contains(id);
    }

    private static TranslationStatus status(TranslationRow row) {
        if (!row.issues().isEmpty()) {
            return TranslationStatus.INVALID;
        }
        return row.translatedText().isBlank()
                ? TranslationStatus.UNTRANSLATED
                : TranslationStatus.TRANSLATED;
    }

    private static String searchableText(TranslationRow row) {
        return (row.id().sourceFile() + "\n" + row.id().key() + "\n"
                        + row.sourceText() + "\n" + row.translatedText())
                .toLowerCase(java.util.Locale.ROOT);
    }
}
