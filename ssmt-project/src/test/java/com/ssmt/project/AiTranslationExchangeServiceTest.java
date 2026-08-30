package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationReviewStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiTranslationExchangeServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsTranslationsNameAndTranslationMemory() throws Exception {
        LocalizationProject project = project();
        Path exchange = temporaryDirectory.resolve("ai.json");
        Path memory = temporaryDirectory.resolve("memory.db");
        AiTranslationExchangeService service = new AiTranslationExchangeService();

        service.exportPackage(exchange, project, "Original Mod", "en", "fr");
        ObjectNode response = (ObjectNode) JSON.readTree(exchange.toFile());
        assertThat(response.path("instructions").asText())
                .contains(
                        "Preserve the schema, IDs, source strings, tokens, formatting,"
                                + " and line breaks")
                .contains("do not invent lore or mechanics absent from the source");
        assertThat(response.withArray("entries").get(0).path("relativeFilePath").asText())
                .isEqualTo("data/strings/strings.json");
        assertThat(response.withArray("entries").get(0).path("contentType").asText())
                .isEqualTo("json");
        assertThat(response.withArray("entries").get(0).path("internalId").asText())
                .isEqualTo("/welcome");
        assertThat(response.withArray("entries").get(0).path("provenance").asText())
                .isEqualTo("MANUAL_IMPORT");
        response.put("translatedModName", "Mod traduit");
        ((ObjectNode) response.withArray("entries").get(0))
                .put("translation", "Bonjour %s");
        JSON.writerWithDefaultPrettyPrinter().writeValue(exchange.toFile(), response);

        AiTranslationImportResult result =
                service.importResponse(exchange, project, memory);

        assertThat(result.importedEntries()).isEqualTo(1);
        assertThat(result.project().entries().getFirst().translatedText())
                .isEqualTo("Bonjour %s");
        assertThat(result.project().entries().getFirst().provenance())
                .isEqualTo(TranslationProvenance.AI_TRANSLATED);
        assertThat(result.project().patchName())
                .isEqualTo("Mod traduit (Original Mod)");
        try (SqliteTranslationMemory translationMemory =
                SqliteTranslationMemory.open(memory)) {
            assertThat(translationMemory.findAll()).hasSize(1);
            assertThat(translationMemory.findAll().getFirst().provenance())
                    .isEqualTo(TranslationProvenance.AI_TRANSLATED);
        }
    }

    @Test
    void rejectsChangedSourceText() throws Exception {
        LocalizationProject project = project();
        Path exchange = temporaryDirectory.resolve("ai.json");
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        service.exportPackage(exchange, project, "Original Mod", "en", "fr");
        ObjectNode response = (ObjectNode) JSON.readTree(exchange.toFile());
        ((ObjectNode) response.withArray("entries").get(0)).put("source", "changed");
        JSON.writeValue(exchange.toFile(), response);

        assertThatThrownBy(() -> service.importResponse(exchange, project, null))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("protected source");
    }

    @Test
    void rejectsBlankAndProtectedTokenFailuresTransactionally() throws Exception {
        LocalizationProject project = project();
        Path exchange = temporaryDirectory.resolve("ai.json");
        Path memory = temporaryDirectory.resolve("memory.db");
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        service.exportPackage(exchange, project, "Original Mod", "en", "fr");

        assertThatThrownBy(() -> service.importResponse(exchange, project, memory))
                .isInstanceOf(AiImportValidationException.class)
                .hasMessageContaining("blank translation");
        assertThat(memory).doesNotExist();

        ObjectNode response = (ObjectNode) JSON.readTree(exchange.toFile());
        ((ObjectNode) response.withArray("entries").get(0))
                .put("translation", "Bonjour");
        JSON.writeValue(exchange.toFile(), response);

        assertThatThrownBy(() -> service.importResponse(exchange, project, memory))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("protected syntax");
        assertThat(memory).doesNotExist();
    }

    @Test
    void importsResponseWhenTranslationIdenticalToSourceContainingStraySyntax()
            throws Exception {
        LocalizationProject project = new LocalizationProject(
                1,
                "original.mod",
                "original.fr",
                "Original French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "/hint",
                        "Insert the {cartridge} now",
                        "")));
        Path exchange = temporaryDirectory.resolve("stray-syntax.json");
        Path memory = temporaryDirectory.resolve("memory.db");
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        service.exportPackage(exchange, project, "Original Mod", "en", "fr");
        ObjectNode response = (ObjectNode) JSON.readTree(exchange.toFile());
        String source = response.withArray("entries").get(0).path("source").asText();
        ((ObjectNode) response.withArray("entries").get(0)).put("translation", source);
        JSON.writeValue(exchange.toFile(), response);

        AiTranslationImportResult result = service.importResponse(exchange, project, memory);

        assertThat(result.importedEntries()).isEqualTo(1);
        assertThat(result.project().entries().getFirst().translatedText())
                .isEqualTo("Insert the {cartridge} now");
    }

    @Test
    void rejectsRemovalOfTrailingRequiredLineBreak() throws Exception {
        LocalizationProject project = new LocalizationProject(
                1,
                "original.mod",
                "original.fr",
                "Original French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "/line",
                        "Source line\n",
                        "")));
        Path exchange = temporaryDirectory.resolve("line-break.json");
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        service.exportPackage(exchange, project, "Original Mod", "en", "fr");
        ObjectNode response = (ObjectNode) JSON.readTree(exchange.toFile());
        ((ObjectNode) response.withArray("entries").get(0))
                .put("translation", "Ligne traduite");
        JSON.writeValue(exchange.toFile(), response);

        assertThatThrownBy(() -> service.importResponse(exchange, project, null))
                .isInstanceOf(AiImportValidationException.class)
                .hasMessageContaining("line breaks");
    }

    @Test
    void explicitlyApprovesEveryValidatedAiResultAndRetainsLineage() throws Exception {
        LocalizationProject project = project();
        Path exchange = temporaryDirectory.resolve("approved-ai.json");
        Path memory = temporaryDirectory.resolve("memory.db");
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        service.exportPackage(exchange, project, "Original Mod", "en", "fr");
        ObjectNode response = (ObjectNode) JSON.readTree(exchange.toFile());
        response.put("providerId", "configured-openai");
        response.put("providerModel", "review-model");
        response.put("providerVersion", "2026-08");
        ((ObjectNode) response.withArray("entries").get(0))
                .put("translation", "Bonjour %s");
        JSON.writeValue(exchange.toFile(), response);

        AiTranslationImportResult result = service.importResponse(
                exchange, project, memory, AiImportPolicy.APPROVE_ALL_VALIDATED);

        assertThat(result.project().entries().getFirst().provenance())
                .isEqualTo(TranslationProvenance.HUMAN_EDITED);
        try (SqliteTranslationMemory translationMemory =
                SqliteTranslationMemory.open(memory)) {
            var stored = translationMemory.findAll().getFirst();
            assertThat(stored.provenance()).isEqualTo(TranslationProvenance.HUMAN_EDITED);
            assertThat(translationMemory.findGenerationMetadata(stored.id()).orElseThrow())
                    .satisfies(metadata -> {
                        assertThat(metadata.providerId()).isEqualTo("configured-openai");
                        assertThat(metadata.modelOrLanguagePackage()).isEqualTo("review-model");
                        assertThat(metadata.providerVersion()).isEqualTo("2026-08");
                        assertThat(metadata.aiRefined()).isTrue();
                        assertThat(metadata.reviewStatus())
                                .isEqualTo(TranslationReviewStatus.APPROVED);
                    });
        }
    }

    @Test
    void keepsExactDestinationNameWhenEntriesFitInOnePart() throws Exception {
        Path destination = temporaryDirectory.resolve("words.json");
        AiTranslationExchangeService service = new AiTranslationExchangeService();

        List<Path> parts = service.exportPackage(
                destination, project(), "Original Mod", "en", "fr", 250);

        assertThat(parts).containsExactly(destination);
        assertThat(destination).exists();
    }

    @Test
    void splitsIntoSiblingNumberedFilesWhenEntriesExceedMaximum() throws Exception {
        Path destination = temporaryDirectory.resolve("words.json");
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        LocalizationProject project = projectWithEntries(5);

        List<Path> parts = service.exportPackage(
                destination, project, "Original Mod", "en", "fr", 2);

        assertThat(parts).extracting(part -> String.valueOf(part.getFileName()))
                .containsExactly("words1.json", "words2.json", "words3.json");
        Set<String> exportedIds = new HashSet<>();
        for (int index = 0; index < parts.size(); index++) {
            ObjectNode part = (ObjectNode) JSON.readTree(parts.get(index).toFile());
            int expectedSize = index < 2 ? 2 : 1;
            assertThat(part.withArray("entries")).hasSize(expectedSize);
            part.withArray("entries").forEach(entry -> exportedIds.add(entry.path("id").asText()));
        }
        assertThat(exportedIds).hasSize(5);
    }

    @Test
    void rejectsNonPositiveMaximumEntriesPerPart() {
        AiTranslationExchangeService service = new AiTranslationExchangeService();
        Path destination = temporaryDirectory.resolve("words.json");

        assertThatThrownBy(() -> service.exportPackage(
                destination, project(), "Original Mod", "en", "fr", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumEntriesPerPart must be positive");
    }

    private static LocalizationProject project() {
        return new LocalizationProject(
                1,
                "original.mod",
                "original.fr",
                "Original French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "/welcome",
                        "Hello %s",
                        "")));
    }

    private static LocalizationProject projectWithEntries(int count) {
        List<ProjectEntry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            entries.add(new ProjectEntry(
                    Path.of("data/strings/strings.json"),
                    "/entry" + index,
                    "Source " + index,
                    ""));
        }
        return new LocalizationProject(
                1, "original.mod", "original.fr", "Original French", entries);
    }
}
