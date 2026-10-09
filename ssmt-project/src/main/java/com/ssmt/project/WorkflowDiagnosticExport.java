package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Explicit, bounded diagnostic export; reads only logs the user selects. */
public final class WorkflowDiagnosticExport {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Writes a new external JSON report, refusing links, source-contained paths and overwrites. */
    public void write(Path destination, List<Path> protectedSources, String operation, String detail,
            List<Path> selectedLogs) throws ProjectException {
        if (selectedLogs.size() > 8) { throw new ProjectException("Select at most eight diagnostic logs"); }
        try {
            Path target = destination.toAbsolutePath().normalize();
            Path parent = target.getParent();
            if (parent == null) { throw new ProjectException("Diagnostic export needs a file path"); }
            Path resolved = parent.toRealPath().resolve(target.getFileName());
            for (Path source : protectedSources) {
                if (resolved.startsWith(source.toRealPath())) {
                    throw new ProjectException("Diagnostic export must be outside the source mod");
                }
            }
            ObjectNode report = JSON.createObjectNode().put("schema_version", 1)
                    .put("operation", operation).put("detail", detail);
            Package application = WorkflowDiagnosticExport.class.getPackage();
            report.put("version", java.util.Objects.toString(application.getImplementationVersion(), "UNKNOWN"));
            report.put("commit", packagedCommit());
            var logs = report.putArray("user_selected_logs");
            for (Path log : selectedLogs) {
                Path name = log.getFileName();
                if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(log)
                        || name == null || Files.size(log) > 1024L * 1024L) {
                    throw new ProjectException("Each diagnostic log must be a regular file of at most 1 MiB");
                }
                byte[] bytes;
                try (var input = Files.newInputStream(log, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = input.readNBytes(1024 * 1024 + 1);
                }
                if (bytes.length > 1024 * 1024) { throw new ProjectException("Diagnostic log grew beyond 1 MiB"); }
                logs.addObject().put("name", name.toString())
                        .put("sha256", digest(bytes))
                        .put("content_base64", java.util.Base64.getEncoder().encodeToString(bytes));
            }
            byte[] contents = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
            WorkflowOperation.beginPublication();
            Files.write(resolved, contents,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ProjectException("Could not export diagnostics: " + exception.getMessage(), exception);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String packagedCommit() {
        try {
            Path location = Path.of(WorkflowDiagnosticExport.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            if (!Files.isRegularFile(location)) { return "UNKNOWN"; }
            try (var jar = new java.util.jar.JarFile(location.toFile())) {
                var manifest = jar.getManifest();
                return manifest == null ? "UNKNOWN" : java.util.Objects.toString(
                        manifest.getMainAttributes().getValue("Implementation-Commit"), "UNKNOWN");
            }
        } catch (IOException | java.net.URISyntaxException | RuntimeException exception) {
            return "UNKNOWN";
        }
    }
}
