package com.ssmt.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a deterministic, author-shareable CSV translation report. */
public final class TranslationReportExporter {
    public void write(Path destination, LocalizationProject project) throws ProjectException {
        try {
            Files.writeString(destination, render(project), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ProjectException("Could not write translation report " + destination, exception);
        }
    }

    /** Renders before publication so required reports participate in the build transaction. */
    public String render(LocalizationProject project) {
        StringBuilder csv = new StringBuilder("source_file,key,status,provenance,source,translation\r\n");
        for (ProjectEntry entry : project.entries()) {
            append(csv, entry.sourceFile().toString().replace('\\', '/'));
            append(csv, entry.key());
            append(csv, entry.translatedText().isBlank() ? "UNTRANSLATED" : "DRAFT");
            append(csv, entry.provenance().name());
            append(csv, entry.originalText());
            csv.append(quote(entry.translatedText())).append("\r\n");
        }
        return csv.toString();
    }

    private static void append(StringBuilder csv, String value) {
        csv.append(quote(value)).append(',');
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
