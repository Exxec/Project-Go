package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CandidateArchiveAuditTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    private static void zip(Path directory, Path archive, String tamperFile) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(archive)); var walk = Files.walk(directory)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                String name = directory.relativize(path).toString().replace('\\', '/');
                output.putNextEntry(new ZipEntry(name));
                output.write(name.equals(tamperFile) ? "id,name\nx,tampered\n".getBytes(StandardCharsets.UTF_8)
                        : Files.readAllBytes(path));
                output.closeEntry();
            }
        }
    }

    /** A published translated copy and the workspace manifest the build wrote for it. */
    private record Published(Path output, Path manifest) { }

    private Published publish() throws Exception {
        Path source = temporary.resolve("source");
        Files.createDirectories(source.resolve("data"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"cand\",\"name\":\"Cand\",\"version\":\"2.1\"}");
        Files.writeString(source.resolve("data/a.csv"), "id,name\nx,中文\n");
        var workflow = new TranslationWorkflow(temporary.resolve("owned"));
        var session = workflow.loadMod(source);
        Path request = temporary.resolve("request.json");
        workflow.exportTranslation(session, request);
        var document = (ObjectNode) JSON.readTree(request.toFile());
        document.path("entries").forEach(entry -> ((ObjectNode) entry).put("translation", "English"));
        JSON.writeValue(request.toFile(), document);
        var translated = workflow.importTranslation(session, request);
        Path output = temporary.resolve("output");
        workflow.buildPatch(translated, output);
        return new Published(output, translated.workspace().resolve("installed-copy.json"));
    }

    @Test void aMatchingCleanArchivePassesBothGatesAndTheLedgerIsAcceptedByTheLedgerReader() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("candidate.zip");
        zip(published.output(), archive, "");
        var audit = new CandidateArchiveAudit();
        var result = audit.audit(archive, published.manifest(), temporary.resolve("work"),
                Optional.of("cand"), Optional.of("2.1"));
        assertThat(result.packageIdentity()).isEqualTo(AssuranceSummary.Disposition.PASS);
        assertThat(result.staticValidation()).isEqualTo(AssuranceSummary.Disposition.PASS);
        Path ledger = temporary.resolve("ledger/ledger.json");
        audit.writeLedger(result, ledger);
        assertThat(ledger.resolveSibling(CandidateArchiveAudit.EVIDENCE_FILE)).isRegularFile();
        var summary = new AssuranceLedgerReader().read(ledger, result.candidateSha256());
        assertThat(summary.results()).filteredOn(item -> item.gate() == AssuranceSummary.Gate.PACKAGE_IDENTITY
                || item.gate() == AssuranceSummary.Gate.STATIC_VALIDATION)
                .allMatch(item -> item.disposition() == AssuranceSummary.Disposition.PASS);
        assertThat(summary.results()).filteredOn(item -> item.gate() == AssuranceSummary.Gate.CAMPAIGN)
                .singleElement().satisfies(item ->
                        assertThat(item.disposition()).isEqualTo(AssuranceSummary.Disposition.NOT_TESTED));
        // Running again merges into the same ledger instead of replacing the other gates.
        audit.writeLedger(result, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, result.candidateSha256()).results()).hasSize(13);
    }

    @Test void aChangedFileFailsPackageIdentityAndTheLedgerStatusIsFailed() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("tampered.zip");
        zip(published.output(), archive, "data/a.csv");
        var audit = new CandidateArchiveAudit();
        var result = audit.audit(archive, published.manifest(), temporary.resolve("work"), Optional.empty(),
                Optional.empty());
        assertThat(result.packageIdentity()).isEqualTo(AssuranceSummary.Disposition.FAIL);
        assertThat(result.packageReason()).contains("1 changed");
        assertThat(result.staticValidation()).isEqualTo(AssuranceSummary.Disposition.NOT_TESTED);
        assertThat(result.evidence().path("manifest_comparison").path("changed").get(0).asText())
                .isEqualTo("data/a.csv");
        Path ledger = temporary.resolve("ledger.json");
        audit.writeLedger(result, ledger);
        assertThat(new AssuranceLedgerReader().read(ledger, result.candidateSha256()).status()).isEqualTo("FAILED");
    }

    @Test void identityExpectationsAreChecked() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("candidate.zip");
        zip(published.output(), archive, "");
        var result = new CandidateArchiveAudit().audit(archive, published.manifest(), temporary.resolve("work"),
                Optional.of("other"), Optional.of("9"));
        assertThat(result.packageIdentity()).isEqualTo(AssuranceSummary.Disposition.FAIL);
        assertThat(result.packageReason()).contains("mod id is cand, expected other")
                .contains("mod version is 2.1, expected 9");
    }

    @Test void wrappedArchiveCannotHideFilesOutsideTheSelectedMod() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("wrapped.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive));
                var walk = Files.walk(published.output())) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new ZipEntry("wrapper/mod/"
                        + published.output().relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output);
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry("undeclared.txt"));
            output.write("extra payload".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        var result = new CandidateArchiveAudit().audit(archive, published.manifest(), temporary.resolve("work"),
                Optional.empty(), Optional.empty());
        assertThat(result.packageIdentity()).isEqualTo(AssuranceSummary.Disposition.FAIL);
        assertThat(result.evidence().path("manifest_comparison").path("added").toString())
                .contains("undeclared.txt");
    }

    @Test void manifestSizesAreComparedAndDuplicatePathsAreRefused() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("candidate.zip");
        zip(published.output(), archive, "");
        ObjectNode manifest = (ObjectNode) JSON.readTree(published.manifest().toFile());
        var entries = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.path("inventory");
        var first = (ObjectNode) entries.get(0);
        first.put("bytes", first.path("bytes").asLong() + 1);
        Path changed = temporary.resolve("changed-manifest.json");
        JSON.writeValue(changed.toFile(), manifest);
        var audit = new CandidateArchiveAudit();
        assertThat(audit.audit(archive, changed, temporary.resolve("work"), Optional.empty(), Optional.empty())
                .packageIdentity()).isEqualTo(AssuranceSummary.Disposition.FAIL);
        entries.add(first.deepCopy());
        JSON.writeValue(changed.toFile(), manifest);
        assertThatThrownBy(() -> audit.audit(archive, changed, temporary.resolve("work"),
                Optional.empty(), Optional.empty())).isInstanceOf(ProjectException.class)
                .hasMessageContaining("Repeated manifest path");
    }

    @Test void aModifiedAuditCacheCannotProduceASecondPackagePass() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("candidate.zip");
        zip(published.output(), archive, "");
        Path work = temporary.resolve("work");
        var audit = new CandidateArchiveAudit();
        assertThat(audit.audit(archive, published.manifest(), work, Optional.empty(), Optional.empty())
                .packageIdentity()).isEqualTo(AssuranceSummary.Disposition.PASS);
        Path cached = new ModInputPreparationService().prepare(archive, work).modRoot();
        Files.writeString(cached.resolve("data/a.csv"), "id,name\nx,changed cache\n");
        assertThatThrownBy(() -> audit.audit(archive, published.manifest(), work, Optional.empty(), Optional.empty()))
                .isInstanceOf(ProjectException.class).hasMessageContaining("cached archive differs");
        assertThat(cached.resolve("data/a.csv")).hasContent("id,name\nx,changed cache\n");
    }

    @Test void aCoercedManifestSchemaVersionIsRefused() throws Exception {
        var published = publish();
        Path archive = temporary.resolve("candidate.zip");
        zip(published.output(), archive, "");
        ObjectNode manifest = (ObjectNode) JSON.readTree(published.manifest().toFile());
        Path changed = temporary.resolve("invalid-schema.json");
        for (var version : java.util.List.of(JSON.getNodeFactory().textNode("1"),
                JSON.getNodeFactory().numberNode(1.5))) {
            manifest.set("schema_version", version);
            JSON.writeValue(changed.toFile(), manifest);
            assertThatThrownBy(() -> new CandidateArchiveAudit().audit(archive, changed,
                    temporary.resolve("work"), Optional.empty(), Optional.empty()))
                    .isInstanceOf(ProjectException.class).hasMessageContaining("schema-1");
        }
    }

    @Test void remainingChineseTextNeedsAReviewDecisionNotAPass() throws Exception {
        Path leftover = temporary.resolve("leftover");
        Files.createDirectories(leftover.resolve("data"));
        Files.writeString(leftover.resolve("mod_info.json"), "{\"id\":\"left\",\"name\":\"Left\",\"version\":\"1\"}");
        Files.writeString(leftover.resolve("data/a.csv"), "id,name\nx,中文\n");
        Path manifest = temporary.resolve("manifest.json");
        Files.writeString(manifest, JSON.writeValueAsString(new InstalledCopyEvidence().capture(leftover)));
        Path archive = temporary.resolve("left.zip");
        zip(leftover, archive, "");
        var result = new CandidateArchiveAudit().audit(archive, manifest, temporary.resolve("work"), Optional.empty(),
                Optional.empty());
        assertThat(result.packageIdentity()).isEqualTo(AssuranceSummary.Disposition.PASS);
        assertThat(result.staticValidation()).isEqualTo(AssuranceSummary.Disposition.REVIEW_REQUIRED);
        assertThat(result.staticReason()).contains("1 leftover");
    }

    @Test void anUnsafeArchiveIsRefusedBeforeAnyComparison() throws Exception {
        Path archive = temporary.resolve("evil.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("../escape.txt"));
            output.write(1);
            output.closeEntry();
        }
        Path manifest = temporary.resolve("manifest.json");
        Files.writeString(manifest, "{\"inventory\":[]}");
        assertThatThrownBy(() -> new CandidateArchiveAudit().audit(archive, manifest, temporary.resolve("work"),
                Optional.empty(), Optional.empty())).isInstanceOf(ProjectException.class).hasMessageContaining("unsafe");
    }
}
