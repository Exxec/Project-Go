package com.ssmt.extractor.csv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.exception.SsmtParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads bounded versioned custom CSV extraction catalogs.
 */
public final class OptInCsvSchemaCatalog {
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Reads and validates a UTF-8 catalog.
     *
     * @param source catalog file
     * @return validated catalog
     * @throws SsmtParseException on malformed or unsafe content
     */
    public OptInCsvSchema read(Path source) throws SsmtParseException {
        try {
            if (!Files.isRegularFile(source) || Files.size(source) > MAX_BYTES) {
                throw new SsmtParseException("Missing or oversized CSV schema catalog", source);
            }
            JsonNode root = JSON.readTree(source.toFile());
            JsonNode fileNodes = required(root, "files");
            if (!fileNodes.isArray()) {
                throw new IllegalArgumentException("Schema files must be an array");
            }
            List<OptInCsvFileSchema> files = new ArrayList<>();
            for (JsonNode file : fileNodes) {
                List<String> identityColumns = textArray(file, "identityColumns");
                List<String> textColumns = textArray(file, "textColumns");
                files.add(new OptInCsvFileSchema(
                        Path.of(text(file, "path")),
                        identityColumns,
                        textColumns));
            }
            return new OptInCsvSchema(
                    required(root, "schemaVersion").intValue(),
                    files);
        } catch (SsmtParseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // Reason: this is a trust boundary for user-authored catalogs;
            // Jackson and path validation expose multiple unchecked failures.
            throw new SsmtParseException("Malformed CSV schema catalog", source, exception);
        }
    }

    /**
     * Writes a deterministic, staged UTF-8 schema catalog.
     *
     * @param destination user-owned catalog destination
     * @param schema validated schema
     * @throws SsmtParseException on write failure
     */
    public void write(Path destination, OptInCsvSchema schema) throws SsmtParseException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", schema.schemaVersion());
        ArrayNode files = root.putArray("files");
        for (OptInCsvFileSchema file : schema.files()) {
            ObjectNode fileNode = files.addObject();
            fileNode.put("path", file.path().toString().replace('\\', '/'));
            ArrayNode identityColumns = fileNode.putArray("identityColumns");
            file.identityColumns().forEach(identityColumns::add);
            ArrayNode textColumns = fileNode.putArray("textColumns");
            file.textColumns().forEach(textColumns::add);
        }
        Path target = destination.toAbsolutePath().normalize();
        Path staging = target.resolveSibling(target.getFileName() + ".ssmt-stage");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.toFile(), root);
            try {
                Files.move(
                        staging,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new SsmtParseException("Could not write CSV schema catalog", destination,
                    exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // Failed cleanup does not invalidate a published catalog.
            }
        }
    }

    private static List<String> textArray(JsonNode file, String name) {
        JsonNode array = required(file, name);
        if (!array.isArray()) {
            throw new IllegalArgumentException("Schema " + name + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Schema " + name + " entry must be text");
            }
            values.add(value.textValue());
        }
        return values;
    }

    private static JsonNode required(JsonNode parent, String name) {
        JsonNode value = parent == null ? null : parent.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing schema field " + name);
        }
        return value;
    }

    private static String text(JsonNode parent, String name) {
        JsonNode value = required(parent, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Schema field must be text: " + name);
        }
        return value.textValue();
    }
}
