package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationMemoryInterchangeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsJsonAndCsvWithUnicode() throws IOException, TranslationMemoryException {
        TranslationDraft first = new TranslationDraft(
                "Captain, ready?", "en", "ja", "艦長、準備完了ですか？", "dialog");
        TranslationDraft second =
                new TranslationDraft("Line\nbreak", "en", "ja", "改行", "tooltip");
        Path sourcePath = temporaryDirectory.resolve("source.db");
        Path json = temporaryDirectory.resolve("translations.json");
        Path csv = temporaryDirectory.resolve("translations.csv");

        try (SqliteTranslationMemory source = SqliteTranslationMemory.open(sourcePath)) {
            source.create(first);
            source.create(second);
            TranslationMemoryInterchange.exportJson(source, json);
            TranslationMemoryInterchange.exportCsv(source, csv);
        }

        assertThat(Files.readString(json, StandardCharsets.UTF_8)).contains("\"schemaVersion\" : 1");
        try (SqliteTranslationMemory jsonTarget =
                        SqliteTranslationMemory.open(temporaryDirectory.resolve("json.db"));
                SqliteTranslationMemory csvTarget =
                        SqliteTranslationMemory.open(temporaryDirectory.resolve("csv.db"))) {
            TranslationMemoryInterchange.importJson(jsonTarget, json);
            TranslationMemoryInterchange.importCsv(csvTarget, csv);
            assertThat(jsonTarget.findAll()).extracting(TranslationEntry::translatedText)
                    .containsExactly(first.translatedText(), second.translatedText());
            assertThat(csvTarget.findAll()).extracting(TranslationEntry::sourceText)
                    .containsExactly(first.sourceText(), second.sourceText());
        }
    }

    @Test
    void rejectsUnsupportedJsonVersionWithoutChangingDatabase()
            throws IOException, TranslationMemoryException {
        Path input = temporaryDirectory.resolve("future.json");
        Files.writeString(input, """
                {"schemaVersion": 2, "entries": []}
                """, StandardCharsets.UTF_8);
        try (SqliteTranslationMemory memory =
                SqliteTranslationMemory.open(temporaryDirectory.resolve("future.db"))) {
            assertThatThrownBy(() -> TranslationMemoryInterchange.importJson(memory, input))
                    .isInstanceOf(TranslationMemoryException.class);
            assertThat(memory.findAll()).isEmpty();
        }
    }

    @Test
    void rollsBackWholeImportOnIdentityConflict() throws TranslationMemoryException {
        TranslationDraft novel = new TranslationDraft("Novel", "en", "fr", "Nouveau", "");
        TranslationDraft conflict = new TranslationDraft("Existing", "en", "fr", "Existant", "");
        Path exportPath = temporaryDirectory.resolve("conflict.json");
        try (SqliteTranslationMemory source =
                SqliteTranslationMemory.open(temporaryDirectory.resolve("export.db"))) {
            source.create(novel);
            source.create(conflict);
            TranslationMemoryInterchange.exportJson(source, exportPath);
        }
        try (SqliteTranslationMemory target =
                SqliteTranslationMemory.open(temporaryDirectory.resolve("target.db"))) {
            target.create(conflict);
            assertThatThrownBy(() -> TranslationMemoryInterchange.importJson(target, exportPath))
                    .isInstanceOf(TranslationMemoryException.class);
            assertThat(target.findAll()).extracting(TranslationEntry::sourceText)
                    .containsExactly("Existing");
        }
    }
}
