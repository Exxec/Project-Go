package com.ssmt.tm;

import com.ssmt.core.model.TranslationProvenance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Versioned UTF-8 JSON and CSV translation-memory interchange.
 */
public final class TranslationMemoryInterchange {
    private static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VERSION = "schema_version";
    private static final String SOURCE_TEXT = "source_text";
    private static final String SOURCE_LANGUAGE = "source_language";
    private static final String TARGET_LANGUAGE = "target_language";
    private static final String TRANSLATED_TEXT = "translated_text";
    private static final String CONTEXT = "context";
    private static final String PROVENANCE = "provenance";
    private static final String[] CSV_HEADERS = {
        VERSION, SOURCE_TEXT, SOURCE_LANGUAGE, TARGET_LANGUAGE, TRANSLATED_TEXT, CONTEXT
    };

    private TranslationMemoryInterchange() {
    }

    /**
     * Exports deterministic portable JSON.
     *
     * @param memory source memory
     * @param output destination
     * @throws TranslationMemoryException on database or file failure
     */
    public static void exportJson(SqliteTranslationMemory memory, Path output)
            throws TranslationMemoryException {
        ObjectNode document = MAPPER.createObjectNode();
        document.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode entries = document.putArray("entries");
        for (TranslationEntry entry : memory.findAll()) {
            ObjectNode node = entries.addObject();
            node.put("sourceText", entry.sourceText());
            node.put("sourceLanguage", entry.sourceLanguage());
            node.put("targetLanguage", entry.targetLanguage());
            node.put("translatedText", entry.translatedText());
            node.put("context", entry.context());
            node.put("provenance", entry.provenance().name());
        }
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, document);
        } catch (IOException exception) {
            throw new TranslationMemoryException("Could not export JSON to " + output, exception);
        }
    }

    /**
     * Imports a complete JSON document atomically.
     *
     * @param memory target memory
     * @param input source file
     * @throws TranslationMemoryException on malformed input or import failure
     */
    public static void importJson(SqliteTranslationMemory memory, Path input)
            throws TranslationMemoryException {
        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            JsonNode document = MAPPER.readTree(reader);
            if (document == null || document.path("schemaVersion").asInt(-1) != SCHEMA_VERSION) {
                throw new TranslationMemoryException("Unsupported JSON interchange schema");
            }
            JsonNode entries = document.get("entries");
            if (entries == null || !entries.isArray()) {
                throw new TranslationMemoryException("JSON entries must be an array");
            }
            List<TranslationDraft> drafts = new ArrayList<>();
            for (JsonNode entry : entries) {
                drafts.add(new TranslationDraft(
                        requiredText(entry, "sourceText"),
                        requiredText(entry, "sourceLanguage"),
                        requiredText(entry, "targetLanguage"),
                        requiredText(entry, "translatedText"),
                        optionalText(entry, "context"),
                        optionalProvenance(entry)));
            }
            memory.importAll(drafts);
        } catch (IOException | IllegalArgumentException exception) {
            throw new TranslationMemoryException("Could not import JSON from " + input, exception);
        }
    }

    /**
     * Exports deterministic portable CSV.
     *
     * @param memory source memory
     * @param output destination
     * @throws TranslationMemoryException on database or file failure
     */
    public static void exportCsv(SqliteTranslationMemory memory, Path output)
            throws TranslationMemoryException {
        CSVFormat format = CSVFormat.DEFAULT;
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writeRecord(writer, format, (Object[]) CSV_HEADERS);
            for (TranslationEntry entry : memory.findAll()) {
                writeRecord(
                        writer,
                        format,
                        SCHEMA_VERSION,
                        entry.sourceText(),
                        entry.sourceLanguage(),
                        entry.targetLanguage(),
                        entry.translatedText(),
                        entry.context());
            }
        } catch (IOException exception) {
            throw new TranslationMemoryException("Could not export CSV to " + output, exception);
        }
    }

    /**
     * Imports a complete CSV document atomically.
     *
     * @param memory target memory
     * @param input source file
     * @throws TranslationMemoryException on malformed input or import failure
     */
    public static void importCsv(SqliteTranslationMemory memory, Path input)
            throws TranslationMemoryException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();
        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)) {
            if (!parser.getHeaderNames().equals(List.of(CSV_HEADERS))
                    || parser.getHeaderMap().keySet().size() != Set.of(CSV_HEADERS).size()) {
                throw new TranslationMemoryException("Unexpected CSV interchange headers");
            }
            List<TranslationDraft> drafts = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (!Integer.toString(SCHEMA_VERSION).equals(record.get(VERSION))) {
                    throw new TranslationMemoryException("Unsupported CSV interchange schema");
                }
                drafts.add(new TranslationDraft(
                        record.get(SOURCE_TEXT),
                        record.get(SOURCE_LANGUAGE),
                        record.get(TARGET_LANGUAGE),
                        record.get(TRANSLATED_TEXT),
                        record.get(CONTEXT)));
            }
            memory.importAll(drafts);
        } catch (IOException | IllegalArgumentException exception) {
            throw new TranslationMemoryException("Could not import CSV from " + input, exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.textValue();
    }

    private static TranslationProvenance optionalProvenance(JsonNode node) {
        String value = optionalText(node, PROVENANCE);
        return value.isEmpty()
                ? TranslationProvenance.MANUAL_IMPORT
                : TranslationProvenance.valueOf(value);
    }

    private static void writeRecord(Writer writer, CSVFormat format, Object... values)
            throws IOException {
        writer.write(format.format(values));
        writer.write(format.getRecordSeparator());
    }
}
