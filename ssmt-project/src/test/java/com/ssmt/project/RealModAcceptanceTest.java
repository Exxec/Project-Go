package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.scanner.CandidateInventory;
import com.ssmt.scanner.InventoryFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in local evidence: copies inputs, never sends text or promotes unreviewed drafts. */
@EnabledIfSystemProperty(named = "ssmt.realmod.source", matches = ".+")
class RealModAcceptanceTest {
    private static final System.Logger LOG = System.getLogger(RealModAcceptanceTest.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void measureRulesAndReviewOnACopiedRealMod() throws Exception {
        Path original = Path.of(System.getProperty("ssmt.realmod.source")).toRealPath();
        Path requested = Path.of(System.getProperty("ssmt.realmod.out")).toAbsolutePath().normalize();
        Path output = java.util.Objects.requireNonNull(requested.getParent()).toRealPath().resolve(requested.getFileName());
        if (Files.exists(output) || output.startsWith(original) || original.startsWith(output)) {
            throw new IllegalArgumentException("Evidence directory must be new and outside the source");
        }
        var inventory = new CandidateInventory();
        var before = inventory.capture(original);
        Path source = output.resolve("source-copy");
        Files.createDirectories(source);
        try (var walk = Files.walk(original)) {
            int copied = 0;
            for (Path path : walk.sorted().toList()) {
                if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
                    throw new IllegalArgumentException("Linked input cannot be copied: " + path);
                }
                Path destination = source.resolve(original.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    copied++;
                    if (copied % 250 == 0) {
                        LOG.log(System.Logger.Level.INFO, "Real-mod evidence: copied {0} files", copied);
                    }
                }
            }
        }
        assertThat(inventory.capture(source)).isEqualTo(before);
        LOG.log(System.Logger.Level.INFO, "Real-mod evidence: copied source; discovering roles and glossary");
        var service = new LocalizationProjectService();
        var project = service.createUsingBridgeForge(source, "realmod-evidence", "Local evidence only");
        var glossary = new GlossaryService(output.resolve("mod-glossary.json"), null, null, 3);
        var proposal = glossary.propose(source, project);
        project = glossary.apply(source, project);
        ObjectNode document = project.methodDocument();
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("roles.json").toFile(), document);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("glossary-proposal.json").toFile(), proposal);
        var unreadable = new ArrayList<String>();
        var sites = BytecodeCallSites.withDefaultRules().scan(source, unreadable);
        Files.writeString(output.resolve("bytecode-allowlist-PROPOSAL.tsv"), BytecodeCallSites.proposal(sites));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("bytecode-audit.json").toFile(), sites);
        Map<String, Set<String>> drafts = new HashMap<>();
        String draftPath = System.getProperty("ssmt.realmod.drafts", "");
        if (!draftPath.isBlank()) {
            JsonNode prior = JSON.readTree(Path.of(draftPath).toFile());
            for (JsonNode entry : prior.path("entries")) {
                String target = entry.path("translation").asText();
                if (target.isBlank()) {
                    target = prior.path("glossary").path(entry.path("source").asText()).asText();
                }
                if (!target.isBlank()) {
                    drafts.computeIfAbsent(entry.path("source").asText(), ignored -> new HashSet<>()).add(target);
                }
            }
        }
        var approval = PolicyApproval.withDefaultRules();
        var context = approval.forProject(project);
        var entries = new ArrayList<ProjectEntry>();
        ObjectNode report = JSON.createObjectNode().put("schema_version", 1).put("mode", "LOCAL_REAL_MOD_EVIDENCE")
                .put("mod_id", project.sourceModId()).put("source_files", before.size())
                .put("source_inventory_sha256", InventoryFingerprint.tree(before.stream().map(file ->
                        new InventoryFingerprint.File(file.path(), file.bytes(), file.sha256())).toList()))
                .put("units", project.entries().size()).put("rules_version", approval.rulesVersion())
                .put("draft_origin", "existing translation document, treated only as drafts")
                .put("glossary_approved", proposal.approved().size()).put("glossary_proposed", proposal.proposed().size())
                .put("bytecode_sites", sites.size()).put("bytecode_unreadable", unreadable.size())
                .put("LIVE_TEST", "NOT_PERFORMED");
        var review = report.putArray("review");
        int approved = 0;
        int supplied = 0;
        for (ProjectEntry entry : project.entries()) {
            Set<String> options = drafts.getOrDefault(entry.originalText(), Set.of());
            String target = options.size() == 1 ? options.iterator().next() : "";
            String resolved = PolicyApproval.validated(entry.originalText(), target);
            if (!target.isBlank()) {
                supplied++;
            }
            var decision = resolved == null ? new PolicyApproval.Decision(false, "protected syntax differs")
                    : context.evaluate(entry, resolved);
            if (!target.isBlank() && decision.approved()) {
                approved++;
                entries.add(entry.withTranslation(resolved, TranslationProvenance.POLICY_APPROVED));
            } else {
                entries.add(entry.withTranslation("", TranslationProvenance.AI_TRANSLATED));
                review.addObject().put("key", entry.key()).put("file", entry.sourceFile().toString())
                        .put("reason", options.size() > 1 ? "conflicting prior drafts" : target.isBlank()
                                ? "no prior draft" : decision.reason());
            }
        }
        project = project.withEntries(entries);
        service.write(output.resolve("review-project.json"), project);
        report.put("drafts_supplied", supplied).put("policy_approved", approved).put("review_needed", review.size())
                .put("policy_approval_rate", project.entries().isEmpty() ? 0.0 : (double) approved / project.entries().size());
        var bareKeys = report.putArray("json_keys");
        for (JsonNode entry : document.path("entries")) {
            if (entry.path("kind").asText().equals("json-key")) {
                bareKeys.addObject().put("id", entry.path("id").asText()).put("file", entry.path("file").asText())
                        .put("role", entry.path("role").asText("TEXT"))
                        .put("consumer_evidence", "NOT_ESTABLISHED by this run");
            }
        }
        if (review.isEmpty()) {
            service.buildTranslatedCopy(source, output.resolve("candidate"), project);
            report.put("build", "PASS");
            auditCandidate(output, project.sourceModId(), report);
        } else {
            report.put("build", "BLOCKED_REVIEW").put("candidate_audit", "NOT_PERFORMED: no approved candidate");
        }
        assertThat(inventory.capture(original)).isEqualTo(before);
        assertThat(inventory.capture(source)).isEqualTo(before);
        report.put("original_unchanged", true).put("source_copy_unchanged", true);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("evidence.json").toFile(), report);
        LOG.log(System.Logger.Level.INFO, "Real-mod evidence: {0} approved, {1} review-needed, source unchanged", approved, review.size());
    }

    private static void auditCandidate(Path output, String modId, ObjectNode report) throws Exception {
        Path candidate = output.resolve("candidate");
        Path manifest = output.resolve("installed-copy.json");
        JSON.writeValue(manifest.toFile(), new InstalledCopyEvidence().capture(candidate));
        Path archive = output.resolve("candidate.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive)); var walk = Files.walk(candidate)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                zip.putNextEntry(new ZipEntry(candidate.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
        var audit = new CandidateArchiveAudit();
        var result = audit.audit(archive, manifest, output.resolve("archive-inspection"), Optional.of(modId), Optional.empty());
        audit.writeLedger(result, output.resolve("ledger/ledger.json"));
        report.put("candidate_audit", result.packageIdentity().name());
        report.set("candidate_audit_evidence", result.evidence());
    }
}
