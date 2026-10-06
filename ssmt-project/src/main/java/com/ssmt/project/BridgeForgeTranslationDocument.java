package com.ssmt.project;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/** Lossless schema-v1 interchange with BridgeForge, including optional provenance fields. */
public final class BridgeForgeTranslationDocument {
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 250_000;
    private static final ObjectMapper JSON = mapper();
    private static final Set<String> KINDS = Set.of("csv", "json", "json-key", "java", "jar");

    private static ObjectMapper mapper() {
        ObjectMapper result = new ObjectMapper();
        result.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(16 * 1024 * 1024).maxNestingDepth(128).build());
        result.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        return result;
    }

    /** Reads without discarding extension fields or changing translation approval. */
    public ObjectNode read(Path source) throws ProjectException {
        try {
            if (Files.size(source) > MAX_BYTES) {
                throw new IllegalArgumentException("Translation document exceeds 64 MiB");
            }
            JsonNode node = JSON.readTree(source.toFile());
            validate(node);
            return (ObjectNode) node;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read BridgeForge translation document: "
                    + exception.getMessage(), exception);
        }
    }

    /** Writes a new user-owned document; existing work is never overwritten. */
    public void write(Path destination, ObjectNode document) throws ProjectException {
        try {
            validate(document);
            byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Translation document exceeds 64 MiB");
            }
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not write BridgeForge translation document: "
                    + exception.getMessage(), exception);
        }
    }

    /** Validates protected identity and path fields before a document reaches application. */
    public void validate(JsonNode document) {
        if (document == null || !document.isObject()
                || !document.path("schema_version").isIntegralNumber()
                || document.path("schema_version").intValue() != 1) {
            throw new IllegalArgumentException("Expected BridgeForge schema_version 1");
        }
        for (String field : Set.of("mode", "mod_id", "source_language", "target_language", "instructions")) {
            text(document, field);
        }
        JsonNode hashes = document.path("file_hashes");
        JsonNode glossary = document.path("glossary");
        JsonNode entries = document.path("entries");
        if (!hashes.isObject() || !glossary.isObject() || !entries.isArray()
                || !document.path("unreadable").isArray() || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid translation collections or entry count");
        }
        hashes.fields().forEachRemaining(item -> {
            safePath(item.getKey());
            if (!item.getValue().isTextual() || !item.getValue().asText().matches("[a-fA-F0-9]{64}")) {
                throw new IllegalArgumentException("Invalid source hash for " + item.getKey());
            }
        });
        glossary.elements().forEachRemaining(value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Glossary values must be text");
            }
        });
        document.path("unreadable").elements().forEachRemaining(value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Unreadable diagnostics must be text");
            }
        });
        Set<String> ids = new HashSet<>();
        for (JsonNode entry : entries) {
            String id = text(entry, "id");
            String file = text(entry, "file");
            String kind = text(entry, "kind");
            safePath(file);
            text(entry, "source");
            text(entry, "translation");
            if (id.isBlank() || !ids.add(id) || !KINDS.contains(kind)
                    || !entry.path("context").isObject() || !hashes.has(file)) {
                throw new IllegalArgumentException("Invalid or duplicate translation entry " + id);
            }
        }
    }

    static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual()) {
            throw new IllegalArgumentException("Expected text field " + field);
        }
        return node.path(field).textValue();
    }

    static Path safePath(String value) {
        if (value.isBlank() || value.startsWith("/") || value.contains("\\") || value.contains(":")) {
            throw new IllegalArgumentException("Unsafe relative source path " + value);
        }
        Path path = Path.of(value);
        if (path.isAbsolute() || path.normalize().startsWith("..")
                || !path.normalize().toString().replace('\\', '/').equals(value)) {
            throw new IllegalArgumentException("Unsafe relative source path " + value);
        }
        return path;
    }
}
