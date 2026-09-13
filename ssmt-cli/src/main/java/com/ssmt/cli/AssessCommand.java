package com.ssmt.cli;

import com.ssmt.scanner.ArchiveInventory;
import com.ssmt.scanner.CandidateInventory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Read-only inventory assessment; unresolved assurance is always explicit. */
@Command(name = "assess", mixinStandardHelpOptions = true,
        description = "Inventory a candidate directory or ZIP without extracting or modifying it.")
public final class AssessCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Candidate directory or ZIP archive.")
    private Path candidate;
    @Option(names = "--json", description = "Emit deterministic JSON to stdout.")
    private boolean json;
    @Option(names = "--compare-zip", description = "Independently compare directory bytes with this ZIP.")
    private Path compareZip;
    @Option(names = "--archive-root", defaultValue = "",
            description = "Explicit ZIP wrapper for comparison; outside files remain extra.")
    private String archiveRoot;
    @Spec private CommandSpec spec;

    /** Declared metadata, not installed dependency or game compatibility evidence. */
    public record Metadata(String id, String name, String version, String gameVersion,
            List<String> jars, List<com.ssmt.core.model.ModDependency> dependencies) {
        public Metadata {
            jars = List.copyOf(jars);
            dependencies = List.copyOf(dependencies);
        }
    }

    /** Inventory-level report, not a declaration that a mod is valid or compatible. */
    public record Report(int schemaVersion, String inputKind, List<String> metadataPaths,
            String selectedRoot, String rootSelection, String status,
            List<?> files, List<String> trustLimits, String metadataValidity, Metadata metadata,
            com.ssmt.scanner.PackageIdentityAudit.Result packageIdentity) {
        public Report {
            metadataPaths = List.copyOf(metadataPaths);
            files = List.copyOf(files);
            trustLimits = List.copyOf(trustLimits);
        }
    }

    @Override public Integer call() {
        try {
            List<?> entries;
            List<String> metadata;
            String kind;
            if (Files.isDirectory(candidate)) {
                var inventory = new CandidateInventory().capture(candidate);
                entries = inventory;
                metadata = inventory.stream().map(CandidateInventory.Entry::path)
                        .filter(AssessCommand::isMetadata).toList();
                kind = "DIRECTORY";
            } else {
                var inventory = new ArchiveInventory().capture(candidate);
                entries = inventory;
                metadata = inventory.stream().map(ArchiveInventory.Entry::path)
                        .filter(AssessCommand::isMetadata).toList();
                kind = "ZIP";
            }
            boolean selected = metadata.size() == 1;
            String root = selected ? parent(metadata.getFirst()) : "";
            Metadata declared = null;
            String validity = "NOT_ASSESSED";
            if (selected) {
                try {
                    byte[] bytes = metadataBytes(metadata.getFirst(), kind);
                    String expected = entries.stream().map(entry -> {
                        if (entry instanceof CandidateInventory.Entry file) {
                            return file.path().equals(metadata.getFirst()) ? file.sha256() : "";
                        }
                        if (entry instanceof ArchiveInventory.Entry file) {
                            return file.path().equals(metadata.getFirst()) ? file.sha256() : "";
                        }
                        return "";
                    }).filter(hash -> !hash.isEmpty()).findFirst().orElseThrow();
                    if (!expected.equals(hash(bytes))) {
                        throw new java.io.IOException("Selected metadata changed after inventory");
                    }
                    var info = new com.ssmt.scanner.ModInfoReader().read(bytes,
                            candidate.toAbsolutePath().normalize().resolve(root));
                    declared = new Metadata(info.id(), info.name(), info.version(), info.gameVersion(),
                            info.jars(), info.dependencies());
                    validity = "VALID";
                } catch (com.ssmt.core.exception.SsmtParseException exception) {
                    validity = "INVALID";
                }
            }
            com.ssmt.scanner.PackageIdentityAudit.Result identity = null;
            if (compareZip != null) {
                if (!kind.equals("DIRECTORY")) {
                    throw new java.io.IOException("--compare-zip requires a directory candidate");
                }
                identity = new com.ssmt.scanner.PackageIdentityAudit()
                        .compare(candidate, compareZip, archiveRoot);
            } else if (!archiveRoot.isEmpty()) {
                throw new java.io.IOException("--archive-root requires --compare-zip");
            }
            Report report = new Report(1, kind, metadata, root,
                    selected ? "SELECTED" : metadata.isEmpty() ? "MISSING" : "AMBIGUOUS",
                    "ASSESSMENT_ONLY", entries, List.of(
                            "Dependencies are declarations; availability/version compatibility NOT_ASSESSED",
                            "Source/JAR correspondence and origin authority NOT_ASSESSED",
                            "Extraction coverage NOT_ASSESSED",
                            "Archive authentication and extractability NOT_ASSESSED",
                            "Runtime, save compatibility and redistribution rights NOT_TESTED"),
                    validity, declared, identity);
            if (json) {
                spec.commandLine().getOut().println(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(report));
            } else {
                var output = spec.commandLine().getOut();
                output.println("ASSESSMENT_ONLY: " + kind + ", " + entries.size() + " files");
                output.println("Root selection: " + report.rootSelection()
                        + (selected ? " (" + (root.isEmpty() ? "." : root) + ")" : ""));
                metadata.forEach(path -> output.println("Metadata candidate: " + path));
                output.println("Metadata validity: " + validity);
                if (declared != null) { output.println("Declared metadata: " + declared); }
                if (identity != null) {
                    output.println("Package identity: " + (identity.identical() ? "PASS" : "FAIL"));
                    output.println("Missing: " + identity.missing());
                    output.println("Extra: " + identity.extra());
                    output.println("Changed: " + identity.changed());
                }
                report.trustLimits().forEach(output::println);
                entries.forEach(output::println);
            }
            return selected && validity.equals("VALID")
                    && (identity == null || identity.identical()) ? 0 : 1;
        } catch (java.io.IOException | java.io.UncheckedIOException exception) {
            spec.commandLine().getErr().println("Assessment failed: " + exception.getMessage());
            return 1;
        }
    }

    private byte[] metadataBytes(String path, String kind) throws java.io.IOException {
        final int limit = 1024 * 1024;
        if (kind.equals("DIRECTORY")) {
            try (var input = Files.newInputStream(candidate.resolve(path))) {
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) { throw new java.io.IOException("Metadata exceeds 1 MiB"); }
                return bytes;
            }
        }
        try (var zip = new java.util.zip.ZipFile(candidate.toFile())) {
            var entry = zip.getEntry(path);
            if (entry == null) { throw new java.io.IOException("Selected metadata disappeared"); }
            try (var input = zip.getInputStream(entry)) {
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) { throw new java.io.IOException("Metadata exceeds 1 MiB"); }
                return bytes;
            }
        }
    }

    private static String hash(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean isMetadata(String path) {
        return path.equals("mod_info.json") || path.endsWith("/mod_info.json");
    }

    private static String parent(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? "" : path.substring(0, separator);
    }
}
