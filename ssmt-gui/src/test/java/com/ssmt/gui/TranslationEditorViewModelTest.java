package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.validation.ValidationCode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationEditorViewModelTest {
    @Test
    void loadsDeterministicallyAndValidatesEveryEdit() {
        TranslationEditorViewModel model = new TranslationEditorViewModel();
        ExtractedString second =
                new ExtractedString("mod", Path.of("b.json"), "json:/b", "Value %s", -1);
        ExtractedString first =
                new ExtractedString("mod", Path.of("a.json"), "json:/a", "Hello", -1);

        model.load(List.of(second, first));
        TranslationRowId id = new TranslationRowId(Path.of("b.json"), "json:/b");
        model.edit(id, "Valeur");

        assertThat(model.rows()).extracting(row -> row.id().sourceFile().toString())
                .containsExactly("a.json", "b.json");
        assertThat(model.row(id).orElseThrow().issues())
                .extracting(issue -> issue.code())
                .containsExactly(ValidationCode.PRINTF_PLACEHOLDER_MISMATCH);
        assertThat(model.hasErrors()).isTrue();
    }

    @Test
    void rejectsDuplicateAndUnknownRows() {
        TranslationEditorViewModel model = new TranslationEditorViewModel();
        ExtractedString value =
                new ExtractedString("mod", Path.of("a.json"), "json:/a", "Hello", -1);

        assertThatThrownBy(() -> model.load(List.of(value, value)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        model.edit(new TranslationRowId(Path.of("missing"), "key"), "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersByTextAndReviewStatusAndTracksDirtyState() {
        TranslationEditorViewModel model = new TranslationEditorViewModel();
        model.load(List.of(
                new ExtractedString("mod", Path.of("a.json"), "json:/hello", "Hello", -1),
                new ExtractedString("mod", Path.of("b.json"), "json:/bye", "Goodbye", -1)));
        TranslationRowId hello = model.rows().getFirst().id();

        assertThat(model.isDirty()).isFalse();
        model.edit(hello, "Bonjour");

        assertThat(model.isDirty()).isTrue();
        assertThat(model.filter("BONJOUR", java.util.Optional.empty()))
                .singleElement()
                .extracting(TranslationRow::sourceText)
                .isEqualTo("Hello");
        assertThat(model.filter(
                        "", java.util.Optional.of(TranslationStatus.UNTRANSLATED)))
                .singleElement()
                .extracting(TranslationRow::sourceText)
                .isEqualTo("Goodbye");
        model.markSaved();
        assertThat(model.isDirty()).isFalse();
    }

    @Test
    void suggestionsRequireExplicitApplicationAndReviewSupportsBulkSelection() {
        TranslationEditorViewModel model = new TranslationEditorViewModel();
        model.load(List.of(
                new ExtractedString("mod", Path.of("a.json"), "json:/a", "Hello", -1),
                new ExtractedString("mod", Path.of("b.json"), "json:/b", "Bye", -1)));
        TranslationRowId first = model.rows().getFirst().id();
        TranslationRowId second = model.rows().getLast().id();

        model.setSuggestions(first, List.of("Bonjour", "Salut"));

        assertThat(model.row(first).orElseThrow().translatedText()).isEmpty();
        model.applySuggestion(first, 1);
        assertThat(model.row(first).orElseThrow().translatedText()).isEqualTo("Salut");
        model.markReviewed(List.of(first, second));
        assertThat(model.isReviewed(first)).isTrue();
        assertThat(model.isReviewed(second)).isTrue();
    }
}
