package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteTranslationMemoryTest {
    @Test
    void migratesVersionOneDatabaseWithDeterministicLegacyProvenance()
            throws Exception {
        Path database = temporaryDirectory.resolve("legacy-v1.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE translations (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      source_text TEXT NOT NULL,
                      source_language TEXT NOT NULL,
                      target_language TEXT NOT NULL,
                      translated_text TEXT NOT NULL,
                      context TEXT NOT NULL,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL,
                      UNIQUE (source_text, source_language, target_language, context)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO translations VALUES (
                      1, 'Hello', 'en', 'fr', 'Bonjour', 'greeting',
                      '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """);
            statement.executeUpdate("PRAGMA user_version = 1");
        }

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            assertThat(memory.findAll()).singleElement()
                    .extracting(TranslationEntry::provenance)
                    .isEqualTo(TranslationProvenance.MANUAL_IMPORT);
        }
    }
    @Test
    void preservesHigherConfidenceTranslationDuringAutomaticUpsert()
            throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("ranked.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.upsertAll(List.of(new TranslationDraft(
                    "源", "zh", "en", "Human", "name",
                    TranslationProvenance.HUMAN_EDITED)));
            memory.upsertAll(List.of(new TranslationDraft(
                    "源", "zh", "en", "Author", "name",
                    TranslationProvenance.AUTHOR_LOCALIZATION)));

            assertThat(memory.findAll()).singleElement().satisfies(entry -> {
                assertThat(entry.translatedText()).isEqualTo("Human");
                assertThat(entry.provenance())
                        .isEqualTo(TranslationProvenance.HUMAN_EDITED);
            });
        }
    }
    private static final TranslationDraft HELLO =
            new TranslationDraft("Hello", "en", "fr", "Bonjour", "greeting");

    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesIntegrityAndCreatesReadableBackup() throws Exception {
        Path database = temporaryDirectory.resolve("memory.db");
        Path backup = temporaryDirectory.resolve("backup.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", ""));
            assertThat(memory.verifyIntegrity()).isTrue();
            memory.backup(backup);
        }
        try (SqliteTranslationMemory restored = SqliteTranslationMemory.open(backup)) {
            assertThat(restored.verifyIntegrity()).isTrue();
            assertThat(restored.findAll()).singleElement()
                    .extracting(TranslationEntry::translatedText)
                    .isEqualTo("Bonjour");
        }
    }

    @Test
    void createsReadsUpdatesAndDeletesEntries() throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("translations.db");

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            TranslationEntry created = memory.create(HELLO);

            assertThat(created.id()).isPositive();
            assertThat(memory.find(created.id())).contains(created);
            assertThat(memory.findAll()).containsExactly(created);

            TranslationDraft updatedDraft =
                    new TranslationDraft("Hello", "en", "fr", "Salut", "greeting");
            TranslationEntry updated = memory.update(created.id(), updatedDraft);

            assertThat(updated.translatedText()).isEqualTo("Salut");
            assertThat(updated.createdAt()).isEqualTo(created.createdAt());
            assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());

            memory.delete(created.id());
            assertThat(memory.find(created.id())).isEmpty();
        }
    }

    @Test
    void persistsEntriesAcrossReopen() throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("persistent.db");
        long id;
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            id = memory.create(HELLO).id();
        }

        try (SqliteTranslationMemory reopened = SqliteTranslationMemory.open(database)) {
            assertThat(reopened.find(id)).get().extracting(TranslationEntry::sourceText)
                    .isEqualTo("Hello");
        }
    }

    @Test
    void restartingApplicationPreservesMultipleIndependentCatalogsAcrossReopen()
            throws TranslationMemoryException {
        Path catalogA = temporaryDirectory.resolve("catalog-a.db");
        Path catalogB = temporaryDirectory.resolve("catalog-b.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogA)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", "greeting"));
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogB)) {
            memory.create(new TranslationDraft("Goodbye", "en", "fr", "Au revoir", "greeting"));
        }

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogA)) {
            assertThat(memory.verifyIntegrity()).isTrue();
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Bonjour");
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogB)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Au revoir");
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogA)) {
            memory.create(new TranslationDraft("Welcome", "en", "fr", "Bienvenue", "greeting"));
        }

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogA)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactlyInAnyOrder("Bonjour", "Bienvenue");
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(catalogB)) {
            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Au revoir");
        }
    }

    @Test
    void openingHealthyCatalogNeverReinitializesSchemaOrData() throws Exception {
        Path database = temporaryDirectory.resolve("stable.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.create(new TranslationDraft("Hello", "en", "fr", "Bonjour", "greeting"));
        }

        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            assertThat(memory.findAll()).hasSize(1);
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            try (var userVersion = statement.executeQuery("PRAGMA user_version")) {
                assertThat(userVersion.next()).isTrue();
                assertThat(userVersion.getInt(1)).isEqualTo(3);
            }
            try (var rowCount = statement.executeQuery("SELECT COUNT(*) FROM translations")) {
                assertThat(rowCount.next()).isTrue();
                assertThat(rowCount.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void bulkImportGrowsCatalogAndRefreshesExistingTranslation()
            throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("catalog.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.upsertAll(List.of(HELLO));
            memory.upsertAll(List.of(
                    new TranslationDraft(
                            "Hello", "en", "fr", "Salut", "greeting"),
                    new TranslationDraft(
                            "Goodbye", "en", "fr", "Au revoir", "greeting")));

            assertThat(memory.findAll())
                    .extracting(TranslationEntry::translatedText)
                    .containsExactly("Salut", "Au revoir");
        }
    }

    @Test
    void rejectsDuplicateIdentity() throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("duplicates.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            memory.create(HELLO);

            TranslationDraft duplicate =
                    new TranslationDraft("Hello", "en", "fr", "Bonsoir", "greeting");
            assertThatThrownBy(() -> memory.create(duplicate))
                    .isInstanceOf(TranslationMemoryException.class);
        }
    }

    @Test
    void reportsMissingIdsForMutations() throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("missing.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            assertThatThrownBy(() -> memory.update(404L, HELLO))
                    .isInstanceOf(TranslationMemoryException.class);
            assertThatThrownBy(() -> memory.delete(404L))
                    .isInstanceOf(TranslationMemoryException.class);
        }
    }

    @Test
    void returnsDeterministicContextSafeFuzzyMatches() throws TranslationMemoryException {
        Path database = temporaryDirectory.resolve("matches.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
            TranslationEntry first = memory.create(
                    new TranslationDraft("Hello world", "en", "fr", "Bonjour monde", "dialog"));
            memory.create(
                    new TranslationDraft("Hello worlds", "en", "fr", "Bonjour mondes", "dialog"));
            memory.create(
                    new TranslationDraft("Hello world", "en", "fr", "Menu", "menu"));
            memory.create(
                    new TranslationDraft("Hello world", "en", "de", "Hallo Welt", "dialog"));

            TranslationQuery query =
                    new TranslationQuery(" HELLO   WORLD ", "en", "fr", "dialog", 0.8, 1);
            assertThat(memory.findMatches(query))
                    .containsExactly(new TranslationMatch(first, 1.0));
        }
    }

    @Test
    void storesProviderModelVersionGenerationAndReviewMetadata() throws Exception {
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(temporaryDirectory.resolve("metadata.db"))) {
            TranslationEntry entry = memory.create(new TranslationDraft(
                    "源", "zh", "en", "Source", "tooltip",
                    TranslationProvenance.ARGOS_TRANSLATED));
            Instant generated = Instant.parse("2026-08-02T12:00:00Z");
            memory.recordGenerationMetadata(
                    "源", "zh", "en", "tooltip",
                    new TranslationGenerationMetadata(
                            "argos-translate", "zh-en", "1.9.6", generated, false,
                            TranslationReviewStatus.DRAFT));

            assertThat(memory.findGenerationMetadata(entry.id())).contains(
                    new TranslationGenerationMetadata(
                            "argos-translate", "zh-en", "1.9.6", generated, false,
                            TranslationReviewStatus.DRAFT));
        }
    }
}
