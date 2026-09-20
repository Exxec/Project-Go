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
    @Option(names = "--coverage", description = "Run read-only standard extraction coverage on a directory.")
    private boolean coverage;
    @Option(names = "--csv-audit", description = "Review CSV structure without requiring extraction to succeed.")
    private boolean csvAudit;
    @Option(names = "--jar-inventory", description = "Inventory directory JAR entries without loading classes.")
    private boolean jarInventory;
    @Option(names = "--source-manifest", description = "Attest observed directory bytes and metadata before/after assessment.")
    private boolean sourceManifest;
    @Option(names = "--compare-zip", description = "Independently compare directory bytes with this ZIP.")
    private Path compareZip;
    @Option(names = "--compare-input", description = "Read-only competing directory or ZIP input; never establishes origin authority.")
    private Path compareInput;
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

    /** Portable observed handling; counts never imply exhaustive localization. */
    public record Coverage(String path, String handler, String status, int strings, String reason) { }

    /** Explicit provenance limits for this one supplied candidate; never a trust claim. */
    public record SourceAuthority(String origin, String archiveCoverage, String selectedVariant,
            String sourceJarCorrespondence, String competingInputs) { }

    /** One deterministic assessment disposition; MANUAL/BLOCKING never imply compatibility. */
    public record Finding(String severity, String code, String detail) { }

    /** Inventory-level report, not a declaration that a mod is valid or compatible. */
    public record Report(int schemaVersion, String inputKind, List<String> metadataPaths,
            String selectedRoot, String rootSelection, String status,
            List<?> files, List<Finding> findings, List<String> trustLimits,
            String metadataValidity, Metadata metadata,
            com.ssmt.scanner.PackageIdentityAudit.Result packageIdentity,
            String inventorySha256, String candidateSha256, String archiveSha256,
            SourceAuthority sourceAuthority,
            String coverageStatus, List<Coverage> extractionCoverage,
            String jsonGapStatus, List<com.ssmt.extractor.StandardJsonGapAuditor.Finding> jsonGapFindings,
            String csvAuditStatus, List<com.ssmt.extractor.CsvStructureAuditor.Finding> csvFindings,
            String jarInventoryStatus, List<com.ssmt.scanner.JarContents> jarContents,
            String sourceManifestStatus, List<com.ssmt.scanner.SourceTreeManifest.Node> sourceNodes,
            List<com.ssmt.scanner.SourceTreeManifest.Node> sourceNodesAfter) {
        public Report {
            metadataPaths = List.copyOf(metadataPaths);
            files = List.copyOf(files);
            findings = List.copyOf(findings);
            trustLimits = List.copyOf(trustLimits);
            extractionCoverage = List.copyOf(extractionCoverage);
            jsonGapFindings = List.copyOf(jsonGapFindings);
            csvFindings = List.copyOf(csvFindings);
            jarContents = List.copyOf(jarContents);
            sourceNodes = List.copyOf(sourceNodes);
            sourceNodesAfter = List.copyOf(sourceNodesAfter);
        }
    }

    @Override public Integer call() {
        try {
            List<com.ssmt.scanner.SourceTreeManifest.Node> sourceNodes = List.of();
            List<com.ssmt.scanner.SourceTreeManifest.Node> sourceNodesAfter = List.of();
            if (sourceManifest) {
                sourceNodes = new com.ssmt.scanner.SourceTreeManifest().capture(candidate);
            }
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
            List<com.ssmt.scanner.InventoryFingerprint.File> tuples = entries.stream().map(entry -> {
                if (entry instanceof CandidateInventory.Entry file) {
                    return new com.ssmt.scanner.InventoryFingerprint.File(
                            file.path(), file.bytes(), file.sha256());
                }
                var file = (ArchiveInventory.Entry) entry;
                return new com.ssmt.scanner.InventoryFingerprint.File(
                        file.path(), file.bytes(), file.sha256());
            }).toList();
            String inventoryHash = com.ssmt.scanner.InventoryFingerprint.tree(tuples);
            String prefix = root.isEmpty() ? "" : root + "/";
            String candidateHash = selected ? com.ssmt.scanner.InventoryFingerprint.tree(tuples.stream()
                    .filter(file -> file.path().startsWith(prefix))
                    .map(file -> new com.ssmt.scanner.InventoryFingerprint.File(
                            file.path().substring(prefix.length()), file.bytes(), file.sha256())).toList()) : "";
            String archiveHash = kind.equals("ZIP")
                    ? com.ssmt.scanner.InventoryFingerprint.archive(candidate) : "";
            String competingInputs = "NOT_ASSESSED_SINGLE_INPUT_ONLY";
            if (compareInput != null) {
                competingInputs = selected
                        ? selectedCandidateHash(compareInput).equals(candidateHash)
                                ? "COMPETING_INPUT_MATCHES_SELECTED_CANDIDATE"
                                : "COMPETING_INPUT_DIFFERS_FROM_SELECTED_CANDIDATE"
                        : "NOT_ASSESSED_NO_SELECTED_CANDIDATE";
            }
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
            List<com.ssmt.scanner.JarContents> jarContents = new java.util.ArrayList<>();
            String jarStatus = "NOT_ASSESSED";
            if (jarInventory) {
                if (selected) {
                    for (var file : tuples) {
                        if (file.path().startsWith(prefix)
                                && file.path().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                            jarContents.add(kind.equals("DIRECTORY")
                                    ? com.ssmt.scanner.JarContents.inspect(candidate.resolve(root),
                                            Path.of(file.path().substring(prefix.length())), file.sha256())
                                    : com.ssmt.scanner.JarContents.inspectArchiveEntry(candidate,
                                            Path.of(file.path()), file.sha256()));
                        }
                    }
                    if (kind.equals("DIRECTORY")) {
                        if (!entries.equals(new CandidateInventory().capture(candidate))) {
                            throw new java.io.IOException("Candidate changed during JAR inventory");
                        }
                    } else if (!entries.equals(new ArchiveInventory().capture(candidate))) {
                        throw new java.io.IOException("Archive changed during JAR inventory");
                    }
                    jarStatus = "OBSERVED_PAYLOAD_INVENTORY";
                }
            }
            List<com.ssmt.extractor.CsvStructureAuditor.Finding> csvFindings = List.of();
            String csvStatus = "NOT_ASSESSED";
            if (csvAudit) {
                if (declared != null) {
                    var auditor = new com.ssmt.extractor.CsvStructureAuditor();
                    if (kind.equals("DIRECTORY")) {
                        List<Path> csvPaths = tuples.stream().filter(file -> file.path().startsWith(prefix))
                                .map(file -> Path.of(file.path().substring(prefix.length()))).toList();
                        csvFindings = auditor.audit(candidate.resolve(root), csvPaths);
                        if (!entries.equals(new CandidateInventory().capture(candidate))) {
                            throw new java.io.IOException("Candidate changed during CSV review");
                        }
                    } else {
                        csvFindings = auditor.auditBytes(archiveCsvEntries(candidate, tuples, prefix));
                        if (!entries.equals(new ArchiveInventory().capture(candidate))) {
                            throw new java.io.IOException("Archive changed during CSV review");
                        }
                    }
                    csvStatus = "OBSERVED_ADVISORY_STRUCTURE";
                }
            }
            List<Coverage> observedCoverage = List.of();
            List<com.ssmt.extractor.StandardJsonGapAuditor.Finding> jsonGapFindings = List.of();
            String jsonGapStatus = "NOT_ASSESSED";
            String coverageStatus = "NOT_ASSESSED";
            if (coverage) {
                if (!kind.equals("DIRECTORY")) {
                    throw new java.io.IOException("--coverage currently requires a directory candidate");
                }
                if (declared != null) {
                    try {
                        var coordinator = new com.ssmt.extractor.ExtractionCoordinator(List.of(
                                new com.ssmt.extractor.csv.StandardCsvFileExtractor(),
                                new com.ssmt.extractor.json.StandardJsonFileExtractor(),
                                new com.ssmt.extractor.bytecode.ClassStringExtractor(),
                                new com.ssmt.extractor.text.MissionTextExtractor()));
                        var extracted = coordinator.extractMod(declared.id(), candidate.resolve(root));
                        observedCoverage = extracted.fileCoverage().stream().map(file -> new Coverage(
                                file.sourceFile().toString().replace('\\', '/'), file.handler(),
                                file.status(), file.extractedStrings(), file.reason())).toList();
                        jsonGapFindings = new com.ssmt.extractor.StandardJsonGapAuditor()
                                .audit(candidate.resolve(root), declared.id(), extracted);
                        var entryCoverage = new com.ssmt.extractor.JarEntryCoverage();
                        Path selectedRoot = candidate.resolve(root);
                        jarContents = jarContents.stream().map(contents -> {
                            try {
                                var byPath = entryCoverage.audit(selectedRoot.resolve(contents.path()),
                                                Path.of(contents.path()), extracted).stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                com.ssmt.extractor.JarEntryCoverage.Entry::path,
                                                java.util.function.Function.identity()));
                                var covered = contents.entries().stream().map(entry -> {
                                    var handling = byPath.get(entry.path());
                                    return new com.ssmt.scanner.JarContents.Entry(entry.path(), entry.bytes(),
                                            entry.sha256(), entry.category(), handling.status(), handling.reason());
                                }).toList();
                                return new com.ssmt.scanner.JarContents(contents.path(), contents.containerSha256(),
                                        covered, contents.sourceJarCorrespondence());
                            } catch (com.ssmt.core.exception.SsmtParseException exception) {
                                throw new java.io.UncheckedIOException(new java.io.IOException(exception));
                            }
                        }).toList();
                        if (!entries.equals(new CandidateInventory().capture(candidate))) {
                            throw new java.io.IOException("Candidate changed during coverage extraction");
                        }
                        coverageStatus = "OBSERVED_STANDARD_EXTRACTION";
                        jsonGapStatus = "OBSERVED_REVIEW_ONLY";
                    } catch (com.ssmt.core.exception.SsmtParseException exception) {
                        throw new java.io.IOException("Coverage extraction failed: " + exception.getMessage(), exception);
                    }
                }
            }
            if (compareZip != null) {
                if (!kind.equals("DIRECTORY")) {
                    throw new java.io.IOException("--compare-zip requires a directory candidate");
                }
                identity = new com.ssmt.scanner.PackageIdentityAudit()
                        .compare(candidate, compareZip, archiveRoot);
            } else if (!archiveRoot.isEmpty()) {
                throw new java.io.IOException("--archive-root requires --compare-zip");
            }
            String sourceStatus = "NOT_ASSESSED";
            if (sourceManifest) {
                sourceNodesAfter = new com.ssmt.scanner.SourceTreeManifest().capture(candidate);
                if (!sourceNodes.equals(sourceNodesAfter)) {
                    throw new java.io.IOException("Observed source bytes or metadata changed during assessment");
                }
                sourceStatus = "UNCHANGED_OBSERVED_BYTES_AND_METADATA";
            }
            SourceAuthority authority = new SourceAuthority(
                    kind.equals("ZIP")
                            ? "USER_SUPPLIED_ARCHIVE_UNVERIFIED"
                            : "USER_SUPPLIED_DIRECTORY_UNVERIFIED",
                    kind.equals("ZIP")
                            ? "HASHED_CONTAINER_AND_ENTRY_INVENTORY"
                            : "NOT_APPLICABLE_DIRECTORY_INPUT",
                    selected ? "UNIQUE_METADATA_ROOT_SELECTED" : "NO_UNIQUE_METADATA_ROOT",
                    jarInventory
                            ? "NOT_ESTABLISHED_PAYLOADS_OBSERVED"
                            : "NOT_ASSESSED",
                    competingInputs);
            boolean bytecodePresent = tuples.stream().anyMatch(file -> {
                String path = file.path().toLowerCase(java.util.Locale.ROOT);
                return path.endsWith(".class") || path.endsWith(".jar");
            });
            boolean sourcePresent = tuples.stream().anyMatch(file ->
                    file.path().toLowerCase(java.util.Locale.ROOT).endsWith(".java"));
            List<Finding> findings = new java.util.ArrayList<>();
            findings.add(new Finding("SUPPORTED", "READ_ONLY_INVENTORY_CAPTURED",
                    "Candidate bytes were inventoried without extraction or mutation"));
            findings.add(selected
                    ? new Finding(root.isEmpty() ? "SUPPORTED" : "REVIEW",
                            root.isEmpty() ? "DIRECT_ROOT_SELECTED" : "NESTED_WRAPPER_SELECTED",
                            root.isEmpty() ? "mod_info.json is at the supplied root"
                                    : "Selected nested root " + root + " independently of metadata validity")
                    : new Finding("BLOCKING",
                            metadata.isEmpty() ? "MOD_ROOT_MISSING" : "MOD_ROOT_AMBIGUOUS",
                            "Exactly one mod_info.json root is required"));
            if (selected) {
                findings.add(new Finding(validity.equals("VALID") ? "SUPPORTED" : "BLOCKING",
                        validity.equals("VALID") ? "DECLARED_METADATA_PARSED" : "DECLARED_METADATA_INVALID",
                        validity.equals("VALID") ? "Declared metadata was parsed from inventoried bytes"
                                : "Selected mod_info.json is not valid declared metadata"));
            }
            if (bytecodePresent && !sourcePresent) {
                findings.add(new Finding("MANUAL", "BYTECODE_ONLY_BEHAVIOR_UNVERIFIED",
                        "Executable payload exists without observed Java source; semantics require escalation"));
            } else if (bytecodePresent) {
                findings.add(new Finding("REVIEW", "SOURCE_JAR_CORRESPONDENCE_NOT_ESTABLISHED",
                        "Observed source and executable payload names do not establish correspondence"));
            }
            findings.add(new Finding("REVIEW", "SOURCE_AUTHORITY_UNVERIFIED",
                    "Caller-supplied bytes do not establish historical origin authority"));
            findings.add(new Finding("MANUAL", "SAVE_STATE_MIGRATION_NOT_ASSESSED",
                    "Persistent campaign state and upgrade behavior require explicit review"));
            findings.add(new Finding("MANUAL", "INTERNAL_API_USE_NOT_ASSESSED",
                    "Internal game API use requires source or bytecode review"));
            findings.add(new Finding("MANUAL", "LIBRARY_OWNERSHIP_NOT_ASSESSED",
                    "Bundled and undeclared dependency ownership requires explicit review"));
            findings.add(new Finding("MANUAL", "ARCHITECTURE_REDESIGN_NOT_ASSESSED",
                    "Architecture changes cannot be inferred or authorized by inventory"));
            Report report = new Report(1, kind, metadata, root,
                    selected ? "SELECTED" : metadata.isEmpty() ? "MISSING" : "AMBIGUOUS",
                    "ASSESSMENT_ONLY", entries, findings, List.of(
                            "Dependencies are declarations; availability/version compatibility NOT_ASSESSED",
                            "Source/JAR correspondence and origin authority NOT_ASSESSED",
                            "Coverage counts are selected strings only, not exhaustive player-visible content",
                            "Archive authentication and extractability NOT_ASSESSED",
                            "Runtime, save compatibility and redistribution rights NOT_TESTED"),
                    validity, declared, identity, inventoryHash, candidateHash, archiveHash,
                    authority,
                    coverageStatus, observedCoverage, jsonGapStatus, jsonGapFindings,
                    csvStatus, csvFindings, jarStatus, jarContents,
                    sourceStatus, sourceNodes, sourceNodesAfter);
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
                output.println("Inventory SHA-256: " + inventoryHash);
                output.println("Selected candidate SHA-256: " + candidateHash);
                if (!archiveHash.isEmpty()) { output.println("Archive SHA-256: " + archiveHash); }
                output.println("Source authority: " + authority);
                findings.forEach(finding -> output.println("Finding: " + finding));
                output.println("Coverage: " + coverageStatus);
                output.println("JSON gap review: " + jsonGapStatus);
                jsonGapFindings.forEach(finding -> output.println("JSON review: " + finding));
                observedCoverage.forEach(output::println);
                output.println("CSV audit: " + csvStatus);
                csvFindings.forEach(output::println);
                output.println("JAR inventory: " + jarStatus);
                jarContents.forEach(output::println);
                output.println("Source manifest: " + sourceStatus);
                sourceNodes.forEach(output::println);
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

    private static String selectedCandidateHash(Path input) throws java.io.IOException {
        List<com.ssmt.scanner.InventoryFingerprint.File> tuples;
        List<String> metadata;
        if (Files.isDirectory(input)) {
            var inventory = new CandidateInventory().capture(input);
            metadata = inventory.stream().map(CandidateInventory.Entry::path)
                    .filter(AssessCommand::isMetadata).toList();
            tuples = inventory.stream().map(file -> new com.ssmt.scanner.InventoryFingerprint.File(
                    file.path(), file.bytes(), file.sha256())).toList();
        } else {
            var inventory = new ArchiveInventory().capture(input);
            metadata = inventory.stream().map(ArchiveInventory.Entry::path)
                    .filter(AssessCommand::isMetadata).toList();
            tuples = inventory.stream().map(file -> new com.ssmt.scanner.InventoryFingerprint.File(
                    file.path(), file.bytes(), file.sha256())).toList();
        }
        if (metadata.size() != 1) {
            throw new java.io.IOException("Competing input has no unique mod_info.json root");
        }
        String root = parent(metadata.getFirst());
        String prefix = root.isEmpty() ? "" : root + "/";
        return com.ssmt.scanner.InventoryFingerprint.tree(tuples.stream()
                .filter(file -> file.path().startsWith(prefix))
                .map(file -> new com.ssmt.scanner.InventoryFingerprint.File(
                        file.path().substring(prefix.length()), file.bytes(), file.sha256())).toList());
    }

    private static java.util.Map<Path, byte[]> archiveCsvEntries(
            Path archive, List<com.ssmt.scanner.InventoryFingerprint.File> files, String prefix)
            throws java.io.IOException {
        var entries = new java.util.TreeMap<Path, byte[]>();
        try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
            for (var file : files) {
                if (!file.path().startsWith(prefix)
                        || !file.path().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
                    continue;
                }
                var entry = zip.getEntry(file.path());
                if (entry == null || entry.isDirectory()) {
                    throw new java.io.IOException("CSV entry disappeared from archive: " + file.path());
                }
                try (var input = zip.getInputStream(entry)) {
                    entries.put(Path.of(file.path().substring(prefix.length())), input.readNBytes(
                            com.ssmt.extractor.CsvStructureAuditor.MAX_INPUT_BYTES + 1));
                }
            }
        }
        return java.util.Map.copyOf(entries);
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
