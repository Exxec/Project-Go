package com.ssmt.gui;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.ProjectEntry;
import com.ssmt.core.model.TranslationProvenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin presentation controller for translation editing.
 */
public final class TranslationEditorController {
    private final TranslationEditorViewModel model = new TranslationEditorViewModel();
    private final Map<TranslationRowId, TranslationProvenance> provenance =
            new LinkedHashMap<>();

    public TranslationEditorController() {
    }

    /**
     * Applies one user edit through validation.
     *
     * @param id stable row identity
     * @param translatedText new draft
     */
    public void updateTranslation(TranslationRowId id, String translatedText) {
        model.edit(id, translatedText);
        provenance.put(id, TranslationProvenance.HUMAN_EDITED);
    }

    /**
     * @return current immutable rows
     */
    public List<TranslationRow> rows() {
        return model.rows();
    }

    /**
     * @param query free-text query
     * @param status optional review status
     * @return matching rows
     */
    public List<TranslationRow> filter(
            String query,
            Optional<TranslationStatus> status) {
        return model.filter(query, status);
    }

    /**
     * @return whether current drafts have unsaved changes
     */
    public boolean isDirty() {
        return model.isDirty();
    }

    /**
     * Marks current drafts as persisted.
     */
    public void markSaved() {
        model.markSaved();
    }

    /**
     * Sets non-applied exact/fuzzy suggestions.
     *
     * @param id row identity
     * @param suggestions suggestions in display order
     */
    public void setSuggestions(TranslationRowId id, List<String> suggestions) {
        model.setSuggestions(id, suggestions);
    }

    /**
     * @param id row identity
     * @return immutable suggestions for display
     */
    public List<String> suggestions(TranslationRowId id) {
        return model.suggestions(id);
    }

    /**
     * Explicitly applies one user-selected suggestion.
     *
     * @param id row identity
     * @param index suggestion index
     */
    public void applySuggestion(TranslationRowId id, int index) {
        model.applySuggestion(id, index);
    }

    /**
     * Marks selected rows reviewed.
     *
     * @param ids selected row identities
     */
    public void markReviewed(List<TranslationRowId> ids) {
        model.markReviewed(ids);
    }

    /**
     * @param id row identity
     * @return whether the row is marked reviewed
     */
    public boolean isReviewed(TranslationRowId id) {
        return model.isReviewed(id);
    }

    /** Returns the current trust/generation provenance shown in the review queue. */
    public TranslationProvenance provenance(TranslationRowId id) {
        return provenance.getOrDefault(id, TranslationProvenance.MANUAL_IMPORT);
    }

    /** Explicitly approves selected valid nonblank drafts as human-reviewed. */
    public void approve(List<TranslationRowId> ids) {
        for (TranslationRowId id : List.copyOf(ids)) {
            TranslationRow row = model.row(id).orElseThrow();
            if (row.translatedText().isBlank() || !row.issues().isEmpty()) {
                throw new IllegalArgumentException("Only valid nonblank drafts can be approved");
            }
            provenance.put(id, TranslationProvenance.HUMAN_EDITED);
        }
        model.markReviewed(ids);
    }

    /** Explicitly rejects selected drafts by clearing them for regeneration or editing. */
    public void reject(List<TranslationRowId> ids) {
        for (TranslationRowId id : List.copyOf(ids)) {
            model.edit(id, "");
            provenance.put(id, TranslationProvenance.MANUAL_IMPORT);
        }
    }

    /**
     * Loads project entries and their current drafts.
     *
     * @param project portable project
     */
    public void load(LocalizationProject project) {
        List<ExtractedString> extracted = project.entries().stream()
                .map(entry -> new ExtractedString(
                        project.sourceModId(),
                        entry.sourceFile(),
                        entry.key(),
                        entry.originalText(),
                        -1))
                .toList();
        model.load(extracted);
        provenance.clear();
        for (ProjectEntry entry : project.entries()) {
            provenance.put(new TranslationRowId(entry.sourceFile(), entry.key()),
                    entry.provenance());
            if (!entry.translatedText().isEmpty()) {
                model.edit(
                        new TranslationRowId(entry.sourceFile(), entry.key()),
                        entry.translatedText());
            }
        }
        model.markSaved();
    }

    /**
     * Applies current editor drafts to a project copy.
     *
     * @param project project whose metadata and entries are retained
     * @return updated immutable project
     */
    public LocalizationProject applyEdits(LocalizationProject project) {
        List<ProjectEntry> entries = new ArrayList<>();
        for (ProjectEntry entry : project.entries()) {
            TranslationRow row = model.row(
                            new TranslationRowId(entry.sourceFile(), entry.key()))
                    .orElseThrow(() ->
                            new IllegalArgumentException("Missing editor row " + entry.key()));
            TranslationProvenance current = provenance(row.id());
            entries.add(entry.translatedText().equals(row.translatedText())
                            && entry.provenance() == current
                    ? entry
                    : entry.withTranslation(row.translatedText(), current));
        }
        return project.withEntries(entries);
    }
}
