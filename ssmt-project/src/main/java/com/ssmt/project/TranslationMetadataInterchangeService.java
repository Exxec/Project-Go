package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.tm.TranslationGenerationMetadata;
import com.ssmt.tm.TranslationReviewStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable, optional provider-lineage interchange independent of project schema versions. */
public final class TranslationMetadataInterchangeService {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void writeJson(Path destination, Map<String, TranslationGenerationMetadata> values)
            throws ProjectException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 2);
        ObjectNode entries = root.putObject("entries");
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ObjectNode value = entries.putObject(entry.getKey());
            write(value, entry.getValue());
        });
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), root);
        } catch (IOException exception) {
            throw new ProjectException("Could not write translation metadata JSON", exception);
        }
    }

    public Map<String, TranslationGenerationMetadata> readJson(Path source)
            throws ProjectException {
        try {
            var root = JSON.readTree(source.toFile());
            if (root.path("schemaVersion").asInt(1) == 1 || !root.path("entries").isObject()) {
                return Map.of();
            }
            Map<String, TranslationGenerationMetadata> result = new LinkedHashMap<>();
            root.path("entries").fields().forEachRemaining(entry ->
                    result.put(entry.getKey(), read(entry.getValue())));
            return Map.copyOf(result);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read translation metadata JSON", exception);
        }
    }

    public void writeCsv(Path destination, Map<String, TranslationGenerationMetadata> values)
            throws ProjectException {
        StringBuilder csv = new StringBuilder(
                "identity,providerId,modelOrLanguagePackage,providerVersion,generatedAt,"
                        + "aiRefined,reviewStatus\n");
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            TranslationGenerationMetadata value = entry.getValue();
            csv.append(escape(entry.getKey())).append(',')
                    .append(escape(value.providerId())).append(',')
                    .append(escape(value.modelOrLanguagePackage())).append(',')
                    .append(escape(value.providerVersion())).append(',')
                    .append(escape(value.generatedAt().toString())).append(',')
                    .append(value.aiRefined()).append(',')
                    .append(value.reviewStatus()).append('\n');
        });
        try {
            Files.writeString(destination, csv, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ProjectException("Could not write translation metadata CSV", exception);
        }
    }

    public Map<String, TranslationGenerationMetadata> readCsv(Path source)
            throws ProjectException {
        try {
            java.util.List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.getFirst().contains("providerId")) {
                return Map.of();
            }
            Map<String, TranslationGenerationMetadata> result = new LinkedHashMap<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                java.util.List<String> fields = parseCsv(line);
                if (fields.size() != 7) {
                    throw new IllegalArgumentException("Metadata CSV row must contain 7 fields");
                }
                result.put(fields.get(0), new TranslationGenerationMetadata(
                        fields.get(1), fields.get(2), fields.get(3),
                        Instant.parse(fields.get(4)), Boolean.parseBoolean(fields.get(5)),
                        TranslationReviewStatus.valueOf(fields.get(6))));
            }
            return Map.copyOf(result);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read translation metadata CSV", exception);
        }
    }

    private static void write(ObjectNode node, TranslationGenerationMetadata value) {
        node.put("providerId", value.providerId());
        node.put("modelOrLanguagePackage", value.modelOrLanguagePackage());
        node.put("providerVersion", value.providerVersion());
        node.put("generatedAt", value.generatedAt().toString());
        node.put("aiRefined", value.aiRefined());
        node.put("reviewStatus", value.reviewStatus().name());
    }

    private static TranslationGenerationMetadata read(com.fasterxml.jackson.databind.JsonNode node) {
        return new TranslationGenerationMetadata(
                node.path("providerId").asText(), node.path("modelOrLanguagePackage").asText(),
                node.path("providerVersion").asText(),
                Instant.parse(node.path("generatedAt").asText()),
                node.path("aiRefined").asBoolean(),
                TranslationReviewStatus.valueOf(node.path("reviewStatus").asText()));
    }

    private static String escape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static java.util.List<String> parseCsv(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        result.add(value.toString());
        return result;
    }
}
