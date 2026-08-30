package com.ssmt.tm;

import com.ssmt.core.model.TranslationProvenance;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Versioned SQLite implementation of the local translation memory.
 */
public final class SqliteTranslationMemory implements AutoCloseable {
    private static final int SCHEMA_VERSION = 3;
    private static final String SELECT_COLUMNS =
            "id, source_text, source_language, target_language, translated_text,"
                    + " context, provenance, created_at, updated_at";

    private final HikariDataSource dataSource;
    private final Path database;

    private SqliteTranslationMemory(HikariDataSource dataSource, Path database) {
        this.dataSource = dataSource;
        this.database = database;
    }

    /**
     * Opens or creates a translation-memory database.
     *
     * @param database database file
     * @return initialized translation memory
     * @throws TranslationMemoryException when initialization fails
     */
    public static SqliteTranslationMemory open(Path database) throws TranslationMemoryException {
        Path normalized = database.toAbsolutePath().normalize();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + normalized);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setPoolName("ssmt-translation-memory");

        HikariDataSource source = null;
        try {
            source = new HikariDataSource(config);
            migrate(source);
            return new SqliteTranslationMemory(source, normalized);
        } catch (SQLException | RuntimeException exception) {
            if (source != null) {
                source.close();
            }
            throw new TranslationMemoryException(
                    "Could not open translation memory at " + normalized, exception);
        }
    }

    /**
     * Creates an entry.
     *
     * @param draft entry values
     * @return persisted entry
     * @throws TranslationMemoryException on storage or identity failure
     */
    public TranslationEntry create(TranslationDraft draft) throws TranslationMemoryException {
        String now = Instant.now().toString();
        long createdId;
        String sql = """
                INSERT INTO translations (
                    source_text, source_language, target_language, translated_text,
                    context, provenance, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindDraft(statement, draft);
            statement.setString(7, now);
            statement.setString(8, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new TranslationMemoryException("SQLite did not return a generated id");
                }
                createdId = keys.getLong(1);
            }
        } catch (SQLException exception) {
            throw failure("Could not create translation-memory entry", exception);
        }
        return find(createdId).orElseThrow(
                () -> new TranslationMemoryException("Created entry could not be read"));
    }

    /**
     * Finds an entry by id.
     *
     * @param id database id
     * @return entry when present
     * @throws TranslationMemoryException on storage failure
     */
    public Optional<TranslationEntry> find(long id) throws TranslationMemoryException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM translations WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readEntry(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("Could not read translation-memory entry " + id, exception);
        }
    }

    /**
     * Returns all entries in deterministic id order.
     *
     * @return immutable entry list
     * @throws TranslationMemoryException on storage failure
     */
    public List<TranslationEntry> findAll() throws TranslationMemoryException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM translations ORDER BY id";
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            List<TranslationEntry> entries = new ArrayList<>();
            while (results.next()) {
                entries.add(readEntry(results));
            }
            return List.copyOf(entries);
        } catch (SQLException exception) {
            throw failure("Could not list translation-memory entries", exception);
        }
    }

    /** Stores or replaces provider/model lineage for an existing translation identity. */
    public void recordGenerationMetadata(
            String sourceText,
            String sourceLanguage,
            String targetLanguage,
            String context,
            TranslationGenerationMetadata metadata) throws TranslationMemoryException {
        String sql = """
                INSERT INTO translation_generation_metadata (
                    translation_id, provider_id, provider_model, provider_version,
                    generated_at, ai_refined, review_status
                )
                SELECT id, ?, ?, ?, ?, ?, ? FROM translations
                WHERE source_text = ? AND source_language = ? AND target_language = ?
                  AND context = ?
                ON CONFLICT(translation_id) DO UPDATE SET
                    provider_id = excluded.provider_id,
                    provider_model = excluded.provider_model,
                    provider_version = excluded.provider_version,
                    generated_at = excluded.generated_at,
                    ai_refined = excluded.ai_refined,
                    review_status = excluded.review_status
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, metadata.providerId());
            statement.setString(2, metadata.modelOrLanguagePackage());
            statement.setString(3, metadata.providerVersion());
            statement.setString(4, metadata.generatedAt().toString());
            statement.setInt(5, metadata.aiRefined() ? 1 : 0);
            statement.setString(6, metadata.reviewStatus().name());
            statement.setString(7, sourceText);
            statement.setString(8, sourceLanguage);
            statement.setString(9, targetLanguage);
            statement.setString(10, context == null ? "" : context);
            if (statement.executeUpdate() == 0) {
                throw new TranslationMemoryException(
                        "Could not attach generation metadata: translation identity is missing");
            }
        } catch (SQLException exception) {
            throw failure("Could not store translation generation metadata", exception);
        }
    }

    /** Reads provider/model lineage for a translation entry. */
    public Optional<TranslationGenerationMetadata> findGenerationMetadata(long translationId)
            throws TranslationMemoryException {
        String sql = """
                SELECT provider_id, provider_model, provider_version, generated_at,
                       ai_refined, review_status
                FROM translation_generation_metadata WHERE translation_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, translationId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TranslationGenerationMetadata(
                        results.getString("provider_id"),
                        results.getString("provider_model"),
                        results.getString("provider_version"),
                        Instant.parse(results.getString("generated_at")),
                        results.getInt("ai_refined") != 0,
                        TranslationReviewStatus.valueOf(results.getString("review_status"))));
            }
        } catch (SQLException exception) {
            throw failure("Could not read translation generation metadata", exception);
        }
    }

    /**
     * Finds context-safe exact and fuzzy matches.
     *
     * @param query matching parameters
     * @return matches ordered by descending score then ascending id
     * @throws TranslationMemoryException on storage failure
     */
    public List<TranslationMatch> findMatches(TranslationQuery query)
            throws TranslationMemoryException {
        String sql = "SELECT " + SELECT_COLUMNS + """
                 FROM translations
                 WHERE source_language = ? AND target_language = ? AND context = ?
                 ORDER BY id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, query.sourceLanguage());
            statement.setString(2, query.targetLanguage());
            statement.setString(3, query.context());
            try (ResultSet results = statement.executeQuery()) {
                List<TranslationMatch> matches = new ArrayList<>();
                while (results.next()) {
                    TranslationEntry entry = readEntry(results);
                    double score = TextSimilarity.score(query.sourceText(), entry.sourceText());
                    if (score >= query.minimumScore()) {
                        matches.add(new TranslationMatch(entry, score));
                    }
                }
                matches.sort(Comparator.comparingDouble(TranslationMatch::score).reversed()
                        .thenComparing(
                                match -> match.entry().provenance().preference(),
                                Comparator.reverseOrder())
                        .thenComparingLong(match -> match.entry().id()));
                return List.copyOf(matches.subList(0, Math.min(query.limit(), matches.size())));
            }
        } catch (SQLException exception) {
            throw failure("Could not match translation-memory entries", exception);
        }
    }

    /**
     * Finds distinct exact translations regardless of context.
     *
     * <p>Callers may auto-apply only when this returns exactly one value.
     *
     * @param sourceText exact source text
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @return deterministic distinct translations
     * @throws TranslationMemoryException on storage failure
     */
    public List<String> findExactTranslations(
            String sourceText,
            String sourceLanguage,
            String targetLanguage) throws TranslationMemoryException {
        String sql = """
                SELECT DISTINCT translated_text
                FROM translations
                WHERE source_text = ? AND source_language = ? AND target_language = ?
                ORDER BY translated_text
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceText);
            statement.setString(2, sourceLanguage);
            statement.setString(3, targetLanguage);
            try (ResultSet results = statement.executeQuery()) {
                List<String> translations = new ArrayList<>();
                while (results.next()) {
                    translations.add(results.getString(1));
                }
                return List.copyOf(translations);
            }
        } catch (SQLException exception) {
            throw failure("Could not find exact translation-memory entries", exception);
        }
    }

    /**
     * Finds distinct exact translations whose provenance represents human approval.
     * Machine-only drafts are deliberately excluded.
     */
    public List<String> findExactApprovedTranslations(
            String sourceText,
            String sourceLanguage,
            String targetLanguage) throws TranslationMemoryException {
        String sql = """
                SELECT DISTINCT translated_text
                FROM translations
                WHERE source_text = ? AND source_language = ? AND target_language = ?
                  AND provenance IN ('HUMAN_EDITED', 'AUTHOR_LOCALIZATION', 'MANUAL_IMPORT')
                ORDER BY translated_text
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceText);
            statement.setString(2, sourceLanguage);
            statement.setString(3, targetLanguage);
            try (ResultSet results = statement.executeQuery()) {
                List<String> translations = new ArrayList<>();
                while (results.next()) {
                    translations.add(results.getString(1));
                }
                return List.copyOf(translations);
            }
        } catch (SQLException exception) {
            throw failure("Could not find exact approved translation-memory entries", exception);
        }
    }

    /**
     * Imports drafts atomically in list order.
     *
     * @param drafts validated drafts
     * @throws TranslationMemoryException when any insert fails
     */
    public void importAll(List<TranslationDraft> drafts) throws TranslationMemoryException {
        String sql = """
                INSERT INTO translations (
                    source_text, source_language, target_language, translated_text,
                    context, provenance, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        importUsing(drafts, sql);
    }

    /**
     * Atomically grows a catalog, refreshing an existing matching identity.
     *
     * @param drafts translation records to add or refresh
     * @throws TranslationMemoryException when the transaction fails
     */
    public void upsertAll(List<TranslationDraft> drafts)
            throws TranslationMemoryException {
        String sql = """
                INSERT INTO translations (
                    source_text, source_language, target_language, translated_text,
                    context, provenance, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(source_text, source_language, target_language, context)
                DO UPDATE SET
                    translated_text = excluded.translated_text,
                    provenance = excluded.provenance,
                    updated_at = excluded.updated_at
                WHERE CASE excluded.provenance
                    WHEN 'HUMAN_EDITED' THEN 5
                    WHEN 'AUTHOR_LOCALIZATION' THEN 4
                    WHEN 'MANUAL_IMPORT' THEN 3
                    WHEN 'AI_TRANSLATED' THEN 2
                    WHEN 'ARGOS_TRANSLATED' THEN 2
                    WHEN 'TRANSLATE_LOCALLY' THEN 2
                    ELSE 1 END
                  >= CASE translations.provenance
                    WHEN 'HUMAN_EDITED' THEN 5
                    WHEN 'AUTHOR_LOCALIZATION' THEN 4
                    WHEN 'MANUAL_IMPORT' THEN 3
                    WHEN 'AI_TRANSLATED' THEN 2
                    WHEN 'ARGOS_TRANSLATED' THEN 2
                    WHEN 'TRANSLATE_LOCALLY' THEN 2
                    ELSE 1 END
                """;
        importUsing(drafts, sql);
    }

    private void importUsing(List<TranslationDraft> drafts, String sql)
            throws TranslationMemoryException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (TranslationDraft draft : List.copyOf(drafts)) {
                    bindDraft(statement, draft);
                    String now = Instant.now().toString();
                    statement.setString(7, now);
                    statement.setString(8, now);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException | RuntimeException exception) {
            throw new TranslationMemoryException(
                    "Could not atomically import translation-memory entries", exception);
        }
    }

    /**
     * Replaces an entry's editable values.
     *
     * @param id database id
     * @param draft replacement values
     * @return updated entry
     * @throws TranslationMemoryException when missing or on storage failure
     */
    public TranslationEntry update(long id, TranslationDraft draft)
            throws TranslationMemoryException {
        String sql = """
                UPDATE translations SET source_text = ?, source_language = ?,
                    target_language = ?, translated_text = ?, context = ?,
                    provenance = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindDraft(statement, draft);
            statement.setString(7, Instant.now().toString());
            statement.setLong(8, id);
            if (statement.executeUpdate() == 0) {
                throw new TranslationMemoryException("No translation-memory entry with id " + id);
            }
        } catch (SQLException exception) {
            throw failure("Could not update translation-memory entry " + id, exception);
        }
        return find(id).orElseThrow(
                () -> new TranslationMemoryException("Updated entry could not be read"));
    }

    /**
     * Deletes an entry.
     *
     * @param id database id
     * @throws TranslationMemoryException when missing or on storage failure
     */
    public void delete(long id) throws TranslationMemoryException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM translations WHERE id = ?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                throw new TranslationMemoryException("No translation-memory entry with id " + id);
            }
        } catch (SQLException exception) {
            throw failure("Could not delete translation-memory entry " + id, exception);
        }
    }

    /**
     * Runs SQLite's complete integrity check.
     *
     * @return whether SQLite reports the database as healthy
     * @throws TranslationMemoryException on storage failure
     */
    public boolean verifyIntegrity() throws TranslationMemoryException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("PRAGMA integrity_check")) {
            return results.next() && "ok".equalsIgnoreCase(results.getString(1));
        } catch (SQLException exception) {
            throw failure("Could not verify translation-memory integrity", exception);
        }
    }

    /**
     * Checkpoints and copies the database to a staged backup destination.
     *
     * @param destination user-selected backup file
     * @throws TranslationMemoryException on checkpoint or copy failure
     */
    public void backup(Path destination) throws TranslationMemoryException {
        Path target = destination.toAbsolutePath().normalize();
        Path staging = target.resolveSibling(target.getFileName() + ".ssmt-stage");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
            Files.copy(database, staging, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(
                        staging,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new TranslationMemoryException(
                    "Could not back up translation memory", exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (java.io.IOException ignored) {
                // Failed cleanup does not invalidate a successfully published backup.
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static void migrate(HikariDataSource source) throws SQLException {
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet results = statement.executeQuery("PRAGMA user_version")) {
                results.next();
                version = results.getInt(1);
            }
            if (version > SCHEMA_VERSION) {
                throw new SQLException("Unsupported translation-memory schema version " + version);
            }
            if (version == 0) {
                statement.executeUpdate("""
                        CREATE TABLE translations (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            source_text TEXT NOT NULL,
                            source_language TEXT NOT NULL,
                            target_language TEXT NOT NULL,
                            translated_text TEXT NOT NULL,
                            context TEXT NOT NULL,
                            provenance TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL,
                            UNIQUE (source_text, source_language, target_language, context)
                        )
                        """);
                createGenerationMetadataTable(statement);
                statement.executeUpdate("PRAGMA user_version = 3");
            } else if (version == 1) {
                statement.executeUpdate("""
                        ALTER TABLE translations ADD COLUMN provenance TEXT NOT NULL
                        DEFAULT 'MANUAL_IMPORT'
                        """);
                createGenerationMetadataTable(statement);
                statement.executeUpdate("PRAGMA user_version = 3");
            } else if (version == 2) {
                createGenerationMetadataTable(statement);
                statement.executeUpdate("PRAGMA user_version = 3");
            }
        }
    }

    private static void createGenerationMetadataTable(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE translation_generation_metadata (
                    translation_id INTEGER PRIMARY KEY,
                    provider_id TEXT NOT NULL,
                    provider_model TEXT NOT NULL,
                    provider_version TEXT NOT NULL,
                    generated_at TEXT NOT NULL,
                    ai_refined INTEGER NOT NULL,
                    review_status TEXT NOT NULL,
                    FOREIGN KEY (translation_id) REFERENCES translations(id) ON DELETE CASCADE
                )
                """);
    }

    private static void bindDraft(PreparedStatement statement, TranslationDraft draft)
            throws SQLException {
        statement.setString(1, draft.sourceText());
        statement.setString(2, draft.sourceLanguage());
        statement.setString(3, draft.targetLanguage());
        statement.setString(4, draft.translatedText());
        statement.setString(5, draft.context());
        statement.setString(6, draft.provenance().name());
    }

    private static TranslationEntry readEntry(ResultSet results) throws SQLException {
        return new TranslationEntry(
                results.getLong("id"),
                results.getString("source_text"),
                results.getString("source_language"),
                results.getString("target_language"),
                results.getString("translated_text"),
                results.getString("context"),
                TranslationProvenance.valueOf(results.getString("provenance")),
                Instant.parse(results.getString("created_at")),
                Instant.parse(results.getString("updated_at")));
    }

    private static TranslationMemoryException failure(String message, SQLException cause) {
        return new TranslationMemoryException(message, cause);
    }
}
