package com.ssmt.gui;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;

/** Local failure evidence independent of the optional console logging provider. */
final class WorkflowFailureReport {
    private WorkflowFailureReport() { }

    static Path save(Path directory, List<Path> protectedSources, String operation,
            Throwable failure) throws IOException {
        Path storage = resolveWithoutLinks(directory);
        for (Path source : protectedSources) {
            Path protectedRoot = resolveWithoutLinks(source);
            if (storage.startsWith(protectedRoot)) {
                throw new IOException("Diagnostic storage must be outside the source mod");
            }
        }
        Files.createDirectories(storage);
        resolveWithoutLinks(storage);
        var text = new StringWriter();
        try (var writer = new PrintWriter(text)) {
            writer.println("Project Go local workflow failure");
            writer.println("Time: " + Instant.now());
            writer.println("Operation: " + operation);
            writer.println(UserDiagnostic.failed(operation, failure).detail());
            writer.println();
            if (failure != null) {
                failure.printStackTrace(writer);
            }
        }
        Path report = Files.createTempFile(storage, "workflow-failure-", ".log");
        try {
            Files.writeString(report, text.toString(), StandardCharsets.UTF_8);
            return report;
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(report);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    private static Path resolveWithoutLinks(Path supplied) throws IOException {
        Path absolute = supplied.toAbsolutePath().normalize();
        Path checked = absolute.getRoot();
        for (Path component : absolute) {
            checked = checked.resolve(component);
            if (Files.exists(checked, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = Files.readAttributes(
                        checked, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new IOException("Diagnostic storage and source paths must not use links");
                }
            }
        }
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        return existing == null ? absolute
                : existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
    }
}
