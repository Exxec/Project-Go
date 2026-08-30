package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationMemoryMergeServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesReadOnlyThenMergesOnlyMissingAndHigherPreferenceEntries()
            throws Exception {
        Path source = temporaryDirectory.resolve("source.db");
        Path destination = temporaryDirectory.resolve("destination.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(source)) {
            memory.importAll(List.of(
                    draft("new", "New", TranslationProvenance.AI_TRANSLATED),
                    draft("upgrade", "Author", TranslationProvenance.AUTHOR_LOCALIZATION),
                    draft("conflict", "Incoming", TranslationProvenance.AI_TRANSLATED),
                    draft("same", "Same", TranslationProvenance.MANUAL_IMPORT)));
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(destination)) {
            memory.importAll(List.of(
                    draft("upgrade", "Old AI", TranslationProvenance.AI_TRANSLATED),
                    draft("conflict", "Human", TranslationProvenance.HUMAN_EDITED),
                    draft("same", "Same", TranslationProvenance.HUMAN_EDITED)));
        }
        TranslationMemoryMergeService service = new TranslationMemoryMergeService();
        byte[] sourceHash = MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(source));

        TranslationMemoryMergeResult comparison = service.compare(source, destination);
        assertThat(comparison)
                .isEqualTo(new TranslationMemoryMergeResult(4, 1, 1, 1, 1));
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(destination)) {
            assertThat(memory.findAll()).hasSize(3);
        }

        assertThat(service.merge(source, destination)).isEqualTo(comparison);
        assertThat(MessageDigest.getInstance("SHA-256")
                        .digest(java.nio.file.Files.readAllBytes(source)))
                .isEqualTo(sourceHash);
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(destination)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Author", "Human", "Same", "New");
        }
    }

    @Test
    void refusesToCompareOrMergeWhenDestinationCatalogDoesNotExist() throws Exception {
        Path source = temporaryDirectory.resolve("source.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(source)) {
            memory.importAll(List.of(draft("new", "New", TranslationProvenance.AI_TRANSLATED)));
        }
        Path destination = temporaryDirectory.resolve("missing-destination.db");
        TranslationMemoryMergeService service = new TranslationMemoryMergeService();

        assertThatThrownBy(() -> service.compare(source, destination))
                .isInstanceOf(TranslationMemoryException.class)
                .hasMessageContaining("does not exist");
        assertThat(java.nio.file.Files.exists(destination)).isFalse();

        assertThatThrownBy(() -> service.merge(source, destination))
                .isInstanceOf(TranslationMemoryException.class)
                .hasMessageContaining("does not exist");
        assertThat(java.nio.file.Files.exists(destination)).isFalse();
    }

    @Test
    void refusesToMergeCatalogIntoItself() throws Exception {
        Path database = temporaryDirectory.resolve("memory.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            assertThat(memory.verifyIntegrity()).isTrue();
        }
        TranslationMemoryMergeService service = new TranslationMemoryMergeService();

        assertThatThrownBy(() -> service.compare(database, database))
                .isInstanceOf(TranslationMemoryException.class)
                .hasMessageContaining("same file");
    }

    private static TranslationDraft draft(
            String source,
            String translation,
            TranslationProvenance provenance) {
        return new TranslationDraft(source, "zh", "en", translation, "context", provenance);
    }
}
