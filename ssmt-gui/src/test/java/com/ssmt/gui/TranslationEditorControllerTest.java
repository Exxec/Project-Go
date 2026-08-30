package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.project.LocalizationProject;
import com.ssmt.project.ProjectEntry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationEditorControllerTest {

    @Test
    void exposesEditedAndValidatedRowsToTheView() {
        TranslationEditorController controller = new TranslationEditorController();
        LocalizationProject project = new LocalizationProject(
                1,
                "example",
                "example.fr",
                "Example French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Hello %s",
                        "")));
        controller.load(project);
        TranslationRowId id = controller.rows().getFirst().id();

        controller.updateTranslation(id, "Bonjour");

        assertThat(controller.rows()).singleElement()
                .satisfies(row -> assertThat(row.issues()).isNotEmpty());
    }

    @Test
    void roundTripsProjectEntriesWithCurrentEdits() {
        TranslationEditorController controller = new TranslationEditorController();
        LocalizationProject project = new LocalizationProject(
                1,
                "example",
                "example.fr",
                "Example French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Hello",
                        "Bonjour")));

        controller.load(project);
        TranslationRowId id = controller.rows().getFirst().id();
        controller.updateTranslation(id, "Salut");

        assertThat(controller.applyEdits(project).entries().getFirst().translatedText())
                .isEqualTo("Salut");
    }
}
