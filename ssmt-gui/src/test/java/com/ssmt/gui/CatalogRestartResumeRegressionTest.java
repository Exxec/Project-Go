package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.project.ProjectException;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import com.ssmt.tm.TranslationEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end regression coverage for the SQLite restart/resume scenario
 * covering SQLite restart/resume persistence
 * (ADR-033): a shared catalog must survive repeated application restarts
 * across multiple mod projects without ever being destructively reinitialized.
 */
class CatalogRestartResumeRegressionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sharedCatalogSurvivesRestartAcrossTwoIndependentModProjects() throws Exception {
        Path catalog = temporaryDirectory.resolve("shared-catalog.db");

        // 1-2. Create/open catalog A (here, the one shared catalog) and write
        // representative data for the first mod project.
        ProjectWorkspaceController session1 =
                new ProjectWorkspaceController(new TranslationEditorController());
        session1.openOrCreateTranslationMemory(catalog);
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", "mod-a"));
        }

        // 3-4. Close (session1 goes out of scope) and reopen through the
        // existing/open workflow, simulating an application restart.
        ProjectWorkspaceController session2 =
                new ProjectWorkspaceController(new TranslationEditorController());
        session2.verifyTranslationMemory(catalog);

        // 5. Verify all data remains.
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Bonjour");
        }

        // 6. Add data for a second mod project, sharing the same catalog.
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            memory.create(new TranslationDraft("Goodbye", "en", "fr", "Au revoir", "mod-b"));
        }

        // 7. Close and reopen again.
        ProjectWorkspaceController session3 =
                new ProjectWorkspaceController(new TranslationEditorController());
        session3.verifyTranslationMemory(catalog);

        // 8. Verify both projects' data remain independently intact.
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactlyInAnyOrder("Bonjour", "Au revoir");
        }
    }

    @Test
    void openingValidCatalogNeverReinitializesItAndInvalidCatalogFailsVisibly() throws Exception {
        Path catalog = temporaryDirectory.resolve("valid-catalog.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", "greeting"));
        }
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());

        controller.verifyTranslationMemory(catalog);
        controller.openOrCreateTranslationMemory(catalog);

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalog)) {
            assertThat(memory.findAll()).hasSize(1);
        }

        Path invalid = temporaryDirectory.resolve("invalid-catalog.db");
        Files.writeString(invalid, "not a sqlite database");

        assertThatThrownBy(() -> controller.verifyTranslationMemory(invalid))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("Could not open");
        assertThatThrownBy(() -> controller.openOrCreateTranslationMemory(invalid))
                .isInstanceOf(ProjectException.class);
    }

    @Test
    void createNewAndOpenExistingSemanticsStayDistinctForTheSameMissingPath() throws Exception {
        Path notYetCreated = temporaryDirectory.resolve("not-yet-created.db");
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());

        assertThatThrownBy(() -> controller.verifyTranslationMemory(notYetCreated))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("does not exist");
        assertThat(Files.exists(notYetCreated)).isFalse();

        controller.openOrCreateTranslationMemory(notYetCreated);

        assertThat(Files.isRegularFile(notYetCreated)).isTrue();
        controller.verifyTranslationMemory(notYetCreated);
    }

    @Test
    void compareAndMergeRemainNonDestructiveAndRequireAnExistingDestination() throws Exception {
        Path source = temporaryDirectory.resolve("source-catalog.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(source)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", "greeting"));
        }
        Path destination = temporaryDirectory.resolve("destination-catalog.db");
        ProjectWorkspaceController controller =
                new ProjectWorkspaceController(new TranslationEditorController());

        assertThatThrownBy(() -> controller.compareTranslationMemories(source, destination))
                .isInstanceOf(ProjectException.class);
        assertThat(Files.exists(destination)).isFalse();

        controller.openOrCreateTranslationMemory(destination);
        var comparison = controller.compareTranslationMemories(source, destination);
        assertThat(comparison.added()).isEqualTo(1);
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(destination)) {
            assertThat(memory.findAll()).isEmpty();
        }

        controller.mergeTranslationMemories(source, destination);
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(destination)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Bonjour");
        }
    }
}
