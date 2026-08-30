package com.ssmt.cli;

import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import com.ssmt.tm.TranslationMemoryInterchange;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Translation-memory database management commands.
 */
@Command(
        name = "tm",
        description = "Import or export a translation-memory database.",
        subcommands = {
            TranslationMemoryCommand.Export.class,
            TranslationMemoryCommand.Import.class,
            TranslationMemoryCommand.Backup.class,
            TranslationMemoryCommand.Integrity.class
        })
public final class TranslationMemoryCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /**
     * Exports translation memory.
     */
    @Command(name = "export", description = "Export translation memory to JSON or CSV.")
    public static final class Export extends Transfer {
        @Override
        protected void transfer(SqliteTranslationMemory memory, Path file, String format)
                throws TranslationMemoryException {
            if ("json".equals(format)) {
                TranslationMemoryInterchange.exportJson(memory, file);
            } else if ("csv".equals(format)) {
                TranslationMemoryInterchange.exportCsv(memory, file);
            } else {
                throw new IllegalArgumentException("Format must be json or csv");
            }
        }
    }

    /**
     * Imports translation memory.
     */
    @Command(name = "import", description = "Atomically import JSON or CSV translation memory.")
    public static final class Import extends Transfer {
        @Override
        protected void transfer(SqliteTranslationMemory memory, Path file, String format)
                throws TranslationMemoryException {
            if ("json".equals(format)) {
                TranslationMemoryInterchange.importJson(memory, file);
            } else if ("csv".equals(format)) {
                TranslationMemoryInterchange.importCsv(memory, file);
            } else {
                throw new IllegalArgumentException("Format must be json or csv");
            }
        }
    }

    /**
     * Creates a checkpointed database backup.
     */
    @Command(name = "backup", description = "Create a staged translation-memory backup.")
    public static final class Backup implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Backup.class);

        @Parameters(index = "0", description = "SQLite database path.")
        private Path database;

        @Parameters(index = "1", description = "Backup database path.")
        private Path destination;

        @Override
        public Integer call() {
            try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
                memory.backup(destination);
                return 0;
            } catch (TranslationMemoryException exception) {
                LOG.error(CliDiagnostics.explain("Translation-memory backup", exception));
                return 1;
            }
        }
    }

    /**
     * Runs SQLite integrity verification.
     */
    @Command(name = "integrity", description = "Verify translation-memory integrity.")
    public static final class Integrity implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Integrity.class);

        @Parameters(index = "0", description = "SQLite database path.")
        private Path database;

        @Override
        public Integer call() {
            try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
                if (!memory.verifyIntegrity()) {
                    LOG.error("Translation-memory integrity check failed");
                    return 1;
                }
                return 0;
            } catch (TranslationMemoryException exception) {
                LOG.error(CliDiagnostics.explain("Translation-memory integrity check", exception));
                return 1;
            }
        }
    }

    private abstract static class Transfer implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Transfer.class);

        @Parameters(index = "0", description = "SQLite database path.")
        private Path database;

        @Parameters(index = "1", description = "Interchange format: json or csv.")
        private String format;

        @Parameters(index = "2", description = "Interchange file path.")
        private Path file;

        @Override
        public final Integer call() {
            try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(database)) {
                transfer(memory, file, format.toLowerCase(Locale.ROOT));
                return 0;
            } catch (TranslationMemoryException | IllegalArgumentException exception) {
                LOG.error(CliDiagnostics.explain("Translation-memory transfer", exception));
                return 1;
            }
        }

        protected abstract void transfer(
                SqliteTranslationMemory memory,
                Path file,
                String normalizedFormat) throws TranslationMemoryException;
    }
}
