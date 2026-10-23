package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Deterministic project document serialization and atomic file publication. */
final class ProjectDocumentCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    void write(Path destination, LocalizationProject project) throws ProjectException {
        byte[] contents = serialize(project);
        Path target = destination.toAbsolutePath().normalize();
        Path staging = target.resolveSibling(target.getFileName() + ".ssmt-stage");
        try {
            Files.write(staging, contents);
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
            throw new ProjectException("Could not write project " + destination, exception);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // A failed staging cleanup must not hide the original write result.
            }
        }
    }

    byte[] serialize(LocalizationProject project) throws ProjectException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", project.schemaVersion());
        root.put("sourceModId", project.sourceModId());
        root.put("patchId", project.patchId());
        root.put("patchName", project.patchName());
        if (project.methodDocument() != null) {
            root.set("bridgeforgeTranslation", project.methodDocument());
        }
        ArrayNode entries = root.putArray("entries");
        for (ProjectEntry entry : project.entries()) {
            ObjectNode node = entries.addObject();
            node.put("sourceFile", entry.sourceFile().toString().replace('\\', '/'));
            node.put("key", entry.key());
            node.put("originalText", entry.originalText());
            node.put("translatedText", entry.translatedText());
            node.put("provenance", entry.provenance().name());
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (IOException exception) {
            throw new ProjectException("Could not serialize localization project", exception);
        }
    }

    LocalizationProject read(Path source) throws ProjectException {
        try {
            JsonNode root = JSON.readTree(source.toFile());
            List<ProjectEntry> entries = new ArrayList<>();
            for (JsonNode node : required(root, "entries")) {
                entries.add(new ProjectEntry(
                        Path.of(text(node, "sourceFile")),
                        text(node, "key"),
                        text(node, "originalText"),
                        text(node, "translatedText"),
                        node.has("provenance")
                                ? com.ssmt.core.model.TranslationProvenance.valueOf(
                                        text(node, "provenance"))
                                : com.ssmt.core.model.TranslationProvenance.MANUAL_IMPORT));
            }
            return new LocalizationProject(
                    required(root, "schemaVersion").intValue(),
                    text(root, "sourceModId"),
                    text(root, "patchId"),
                    text(root, "patchName"),
                    entries,
                    root.has("bridgeforgeTranslation")
                            ? (ObjectNode) root.path("bridgeforgeTranslation") : null);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read project " + source, exception);
        }
    }

    private static JsonNode required(JsonNode parent, String name) {
        JsonNode value = parent == null ? null : parent.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing project field " + name);
        }
        return value;
    }

    private static String text(JsonNode parent, String name) {
        JsonNode value = required(parent, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Project field is not text: " + name);
        }
        return value.textValue();
    }
}
