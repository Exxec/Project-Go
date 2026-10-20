package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import com.ssmt.scanner.ModInfoReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Audit of the final candidate ZIP (plan G4.1, release gate P6): the archive is extracted with the bounded, traversal-safe
 * preparation service, every file hash is compared with the publication manifest ({@code installed-copy.json} of the
 * workspace, written when the copy was published), the leftover check runs on the extracted tree and the mod identity is
 * read. The result is the evidence for the PACKAGE_IDENTITY and STATIC_VALIDATION gates of an assurance ledger, bound to
 * the extracted candidate's inventory fingerprint. It certifies bytes and static text only, never game behaviour.
 */
public final class CandidateArchiveAudit {
    /** Evidence file name written beside the ledger. */
    public static final String EVIDENCE_FILE = "candidate-audit.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MANIFEST_BYTES = 64 * 1024 * 1024;

    /**
     * Audit outcome.
     *
     * @param candidateSha256 inventory fingerprint of the extracted mod root (what a ledger binds to)
     * @param packageIdentity gate disposition for PACKAGE_IDENTITY
     * @param staticValidation gate disposition for STATIC_VALIDATION
     * @param evidence the evidence document (also what the ledger references by hash)
     */
    public record Result(String candidateSha256, AssuranceSummary.Disposition packageIdentity,
            AssuranceSummary.Disposition staticValidation, String packageReason, String staticReason,
            ObjectNode evidence) {
        public Result {
            evidence = evidence.deepCopy();
        }

        @Override public ObjectNode evidence() {
            return evidence.deepCopy();
        }
    }

    /**
     * Audits one candidate archive.
     *
     * @param archive the candidate ZIP
     * @param manifest publication manifest ({@code installed-copy.json})
     * @param workDir empty directory for the bounded extraction (kept for inspection)
     * @param expectedId required mod id, or empty
     * @param expectedVersion required mod version, or empty
     */
    public Result audit(Path archive, Path manifest, Path workDir, Optional<String> expectedId,
            Optional<String> expectedVersion) throws ProjectException {
        var prepared = new ModInputPreparationService().prepare(archive, workDir);
        Path root = prepared.modRoot();
        try {
            ObjectNode evidence = JSON.createObjectNode().put("schema_version", 1).put("kind", "CANDIDATE_ARCHIVE_AUDIT");
            evidence.put("archive_sha256", prepared.archiveSha256().orElseThrow());
            var inventory = new CandidateInventory().capture(root);
            String candidate = InventoryFingerprint.tree(inventory.stream().map(file ->
                    new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList());
            evidence.put("candidate_sha256", candidate);
            byte[] manifestBytes;
            try (var input = Files.newInputStream(manifest)) {
                manifestBytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            }
            if (manifestBytes.length > MAX_MANIFEST_BYTES) {
                throw new IOException("Publication manifest exceeds 64 MiB");
            }
            evidence.put("manifest_sha256", java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(manifestBytes)));

            var expected = new TreeMap<String, InventoryFingerprint.File>();
            JsonNode manifestRoot = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build().readTree(manifestBytes);
            if (manifestRoot == null || !manifestRoot.path("schema_version").isIntegralNumber()
                    || !manifestRoot.path("schema_version").canConvertToInt()
                    || manifestRoot.path("schema_version").intValue() != 1
                    || !manifestRoot.path("inventory").isArray()) {
                throw new IOException("Expected a schema-1 publication manifest inventory");
            }
            for (JsonNode node : manifestRoot.path("inventory")) {
                String path = BridgeForgeTranslationDocument.text(node, "path");
                BridgeForgeTranslationDocument.safePath(path);
                if (!node.path("bytes").isIntegralNumber() || !node.path("bytes").canConvertToLong()) {
                    throw new IOException("Manifest file size must be an integer: " + path);
                }
                var file = new InventoryFingerprint.File(path, node.path("bytes").longValue(),
                        BridgeForgeTranslationDocument.text(node, "sha256"));
                if (expected.putIfAbsent(path, file) != null) {
                    throw new IOException("Repeated manifest path: " + path);
                }
            }
            var actual = new TreeMap<String, InventoryFingerprint.File>();
            inventory.forEach(file -> actual.put(file.path(),
                    new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())));
            var added = new TreeSet<String>();
            var missing = new TreeSet<String>();
            var changed = new TreeSet<String>();
            var archiveFiles = new com.ssmt.scanner.ArchiveInventory().capture(archive);
            String metadata = archiveFiles.stream().map(com.ssmt.scanner.ArchiveInventory.Entry::path)
                    .filter(path -> path.equalsIgnoreCase("mod_info.json")
                            || path.toLowerCase(java.util.Locale.ROOT).endsWith("/mod_info.json"))
                    .findFirst().orElseThrow(() -> new IOException("Archive metadata missing"));
            String prefix = metadata.substring(0, metadata.length() - "mod_info.json".length());
            archiveFiles.stream().map(com.ssmt.scanner.ArchiveInventory.Entry::path)
                    .filter(path -> !path.startsWith(prefix)).forEach(added::add);
            actual.forEach((path, file) -> {
                if (!expected.containsKey(path)) {
                    added.add(path);
                } else if (!expected.get(path).equals(file)) {
                    changed.add(path);
                }
            });
            expected.keySet().stream().filter(path -> !actual.containsKey(path)).forEach(missing::add);
            ObjectNode comparison = evidence.putObject("manifest_comparison");
            comparison.put("expected_files", expected.size()).put("archive_files", actual.size());
            array(comparison, "added", added);
            array(comparison, "missing", missing);
            array(comparison, "changed", changed);

            var mod = new ModInfoReader().read(root);
            evidence.putObject("mod").put("id", mod.id()).put("version", String.valueOf(mod.version()));
            var problems = new TreeSet<String>();
            if (expected.isEmpty()) {
                problems.add("the manifest lists no files");
            }
            if (!added.isEmpty() || !missing.isEmpty() || !changed.isEmpty()) {
                problems.add("archive differs from the publication manifest (" + added.size() + " added, "
                        + missing.size() + " missing, " + changed.size() + " changed)");
            }
            expectedId.filter(id -> !id.equals(mod.id()))
                    .ifPresent(id -> problems.add("mod id is " + mod.id() + ", expected " + id));
            expectedVersion.filter(version -> !version.equals(String.valueOf(mod.version())))
                    .ifPresent(version -> problems.add("mod version is " + mod.version() + ", expected " + version));
            var packageIdentity = problems.isEmpty() ? AssuranceSummary.Disposition.PASS
                    : AssuranceSummary.Disposition.FAIL;
            String packageReason = problems.isEmpty() ? "archive bytes equal the publication manifest" : String.join("; ", problems);

            var export = new BridgeForgeTranslationService().exportWithRoles(root);
            var leftovers = LeftoverClassifier.classify(root, export, null, java.util.List.of());
            evidence.set("leftover", leftovers.path("by_reason"));
            evidence.put("leftover_total", leftovers.path("total").asInt());
            ArrayNode unreadable = evidence.putArray("unreadable");
            export.path("unreadable").forEach(unreadable::add);
            var staticValidation = AssuranceSummary.Disposition.NOT_TESTED;
            String staticReason = "package identity failed; static validation not trusted";
            if (packageIdentity == AssuranceSummary.Disposition.PASS) {
                boolean clean = leftovers.path("total").asInt() == 0 && export.path("unreadable").isEmpty();
                staticValidation = clean ? AssuranceSummary.Disposition.PASS : AssuranceSummary.Disposition.REVIEW_REQUIRED;
                staticReason = clean ? "no remaining CJK units, no unreadable inputs"
                        : leftovers.path("total").asInt() + " leftover item(s) " + leftovers.path("by_reason")
                                + ", " + export.path("unreadable").size() + " unreadable input(s) need a decision";
            }
            evidence.putObject("gates").put("PACKAGE_IDENTITY", packageIdentity.name())
                    .put("STATIC_VALIDATION", staticValidation.name());
            if (!prepared.archiveSha256().orElseThrow().equals(InventoryFingerprint.archive(archive))
                    || !inventory.equals(new CandidateInventory().capture(root))) {
                throw new IOException("Candidate or archive changed during audit");
            }
            return new Result(candidate, packageIdentity, staticValidation, packageReason, staticReason, evidence);
        } catch (IOException | java.security.NoSuchAlgorithmException
                | com.ssmt.core.exception.SsmtParseException | IllegalArgumentException exception) {
            throw new ProjectException("Could not audit the candidate archive: " + exception.getMessage(), exception);
        }
    }

    private static void array(ObjectNode parent, String name, Iterable<String> values) {
        ArrayNode array = parent.putArray(name);
        values.forEach(array::add);
    }

    /**
     * Writes the evidence file and a ledger beside it. A fresh ledger starts with every gate NOT_TESTED; an existing
     * ledger for the same candidate keeps its other results and references.
     *
     * @param ledgerFile ledger to write (an existing one is merged and replaced atomically)
     * @return the ledger that was written
     */
    public AssuranceLedgerReader.Ledger writeLedger(Result result, Path ledgerFile) throws ProjectException {
        try {
            byte[] evidenceBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result.evidence());
            String evidenceHash = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(evidenceBytes));
            Path directory = java.util.Objects.requireNonNull(ledgerFile.toAbsolutePath().getParent());
            Files.createDirectories(directory);
            var ledger = AssuranceLedgerWriter.merge(ledgerFile, result.candidateSha256(),
                    java.util.Set.of(AssuranceSummary.Gate.PACKAGE_IDENTITY, AssuranceSummary.Gate.STATIC_VALIDATION),
                    java.util.List.of(
                            gateResult(AssuranceSummary.Gate.PACKAGE_IDENTITY, result.packageIdentity(),
                                    result.candidateSha256(), result.packageReason()),
                            gateResult(AssuranceSummary.Gate.STATIC_VALIDATION, result.staticValidation(),
                                    result.candidateSha256(), result.staticReason())),
                    java.util.Map.of(EVIDENCE_FILE, evidenceHash));
            TranslationSnapshot.writeAtomic(directory.resolve(EVIDENCE_FILE), evidenceBytes);
            return ledger;
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            throw new ProjectException("Could not write the ledger: " + exception.getMessage(), exception);
        }
    }

    private static AssuranceSummary.Result gateResult(AssuranceSummary.Gate gate,
            AssuranceSummary.Disposition disposition, String candidate, String reason) {
        return new AssuranceSummary.Result(gate, disposition, candidate, gate.name(),
                disposition == AssuranceSummary.Disposition.NOT_TESTED ? "" : EVIDENCE_FILE, reason);
    }
}
