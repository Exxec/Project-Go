package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Exports a minimal structured project summary without source text or absolute paths.
 */
public final class ProjectDiagnosticExporter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*\\S+");

    /**
     * Writes deterministic UTF-8 diagnostic JSON.
     *
     * @param destination diagnostic output
     * @param project project whose content is summarized
     * @param detail optional user-facing detail, redacted before export
     * @throws ProjectException on write failure
     */
    public void write(
            Path destination,
            LocalizationProject project,
            String detail) throws ProjectException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("projectSchemaVersion", project.schemaVersion());
        root.put("sourceModId", project.sourceModId());
        root.put("patchId", project.patchId());
        root.put("entryCount", project.entries().size());
        root.put("translatedCount", project.entries().stream()
                .filter(entry -> !entry.translatedText().isBlank())
                .count());
        root.put("detail", redact(detail));
        try {
            JSON.writerWithDefaultPrettyPrinter()
                    .writeValue(destination.toAbsolutePath().normalize().toFile(), root);
        } catch (IOException exception) {
            throw new ProjectException("Could not write diagnostic export", exception);
        }
    }

    private static String redact(String detail) {
        if (detail == null) {
            return "";
        }
        String secrets = SECRET.matcher(detail).replaceAll("$1=[REDACTED]");
        return secrets.replaceAll(
                "(?i)([A-Z]:\\\\|/home/|/Users/)[^\\s]+",
                "[REDACTED_PATH]");
    }
}
