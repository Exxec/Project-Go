package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes the bounded, data-only glossary JSON format. */
public final class GlossaryInterchangeService {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void write(Path destination, GlossaryDocument glossary) throws ProjectException {
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), glossary);
        } catch (IOException exception) {
            throw new ProjectException("Could not write glossary " + destination, exception);
        }
    }

    public GlossaryDocument read(Path source) throws ProjectException {
        try {
            if (Files.size(source) > 4L * 1024L * 1024L) {
                throw new ProjectException("Glossary exceeds the 4 MiB safety limit");
            }
            GlossaryDocument glossary = JSON.readValue(source.toFile(), GlossaryDocument.class);
            if (glossary.terms().size() > 10_000) {
                throw new ProjectException("Glossary exceeds the 10,000-term safety limit");
            }
            return glossary;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read glossary " + source, exception);
        }
    }
}
