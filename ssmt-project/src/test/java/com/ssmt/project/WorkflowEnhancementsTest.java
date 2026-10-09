package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.OperationCancelledException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowEnhancementsTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    private Path source() throws Exception {
        Path source = temporary.resolve("source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"enhance\",\"name\":\"Enhance\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"a\":\"Hello %s\",\"b\":\"Goodbye\"}");
        return source;
    }

    private TranslationWorkflow.Session translate(TranslationWorkflow workflow, TranslationWorkflow.Session session)
            throws Exception {
        Path response = temporary.resolve("response-" + java.util.UUID.randomUUID() + ".json");
        workflow.exportTranslation(session, response);
        var document = json.readTree(response.toFile());
        for (var entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", "Translated " + entry.path("source").asText());
        }
        json.writeValue(response.toFile(), document);
        return workflow.importTranslation(session, response);
    }

    @Test void reviewRetainsPreviousTextAndRemovalHistoryAndBlocksUnresolvedInstall() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(temporary.resolve("workspaces"));
        var session = translate(workflow, workflow.loadMod(source));
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"a\":\"Changed %s\"}");
        var refreshed = workflow.loadMod(source);
        byte[] saved = Files.readAllBytes(refreshed.workspace().resolve("project.ssmt.json"));
        var review = workflow.review(refreshed);
        assertThat(review.findings()).anyMatch(f -> f.previousSource().equals("Hello %s")
                && f.previousTranslation().equals("Translated Hello %s"));
        assertThat(review.findings()).anyMatch(f -> f.reason().startsWith("Removed source"));
        assertThatThrownBy(() -> workflow.buildPatch(refreshed, temporary.resolve("output")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("Review unresolved");
        assertThat(Files.readAllBytes(refreshed.workspace().resolve("project.ssmt.json"))).isEqualTo(saved);
        assertThat(session.project().entries()).hasSize(2);
        var corrected = translate(workflow, refreshed);
        assertThat(corrected.needsReview()).isZero();
        workflow.buildPatch(corrected, temporary.resolve("output"));
    }

    @Test void readinessUsesBuilderPlaceholderRulesIncludingBridgeForgeUnits() {
        var project = new LocalizationProject(1, "source", "translated", "Translated", List.of(
                new ProjectEntry(Path.of("data/test.json"), "bf:test", "\u4e2d\u6587 %s $name", "English")));
        assertThat(WorkflowReview.validate(project).findings()).singleElement()
                .satisfies(f -> assertThat(f.reason()).contains("placeholders"));
    }

    @Test void jsonKeyAdviceRetainsUnknownRoleAndDoesNotChangeInterchangeUnits() throws Exception {
        Path source = Path.of("../fixtures/translation-conformance/input");
        var project = new LocalizationProjectService().createUsingBridgeForge(source, "translated", "Translated");
        var before = project.methodDocument();
        assertThat(WorkflowReview.jsonKeys(project)).hasSize(2)
                .allMatch(finding -> finding.reason().contains("role UNKNOWN"));
        assertThat(project.methodDocument()).isEqualTo(before);
    }

    @Test void publicationInventoryDetectsAddedMissingAndChangedBytesAfterRestart() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(temporary.resolve("workspaces"));
        var session = translate(workflow, workflow.loadMod(source));
        Path output = temporary.resolve("installed");
        workflow.buildPatch(session, output);
        assertThat(workflow.installedQuality(session).clear()).isTrue();
        assertThat(workflow.verifyInstalled(session, output)).isEmpty();
        Files.writeString(output.resolve("data/strings/strings.json"), "changed");
        assertThatThrownBy(() -> workflow.installedQuality(session, output))
                .isInstanceOf(ProjectException.class).hasMessageContaining("does not match");
        Files.delete(output.resolve("mod_info.json"));
        Files.writeString(output.resolve("new.txt"), "extra");
        var restarted = new TranslationWorkflow(temporary.resolve("workspaces"));
        var resumed = restarted.loadMod(source);
        assertThat(restarted.verifyInstalled(resumed, output)).containsExactly(
                "Changed: data/strings/strings.json", "Missing: mod_info.json", "Added: new.txt");
        assertThat(Files.readString(source.resolve("data/strings/strings.json"))).contains("Hello %s");
    }

    @Test void observedQualitySeparatesRemainingTextAndUnreadableInput() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"a\":\"\u4e2d\u6587\"}");
        var evidence = new InstalledCopyEvidence();
        var quality = evidence.inspect(source);
        assertThat(quality.leftoverUnits()).isEqualTo(1);
        assertThat(quality.files()).containsExactly(new InstalledCopyEvidence.FileFinding("data/strings/strings.json", 1));
        assertThat(quality.clear()).isFalse();
        Files.createDirectories(source.resolve("jars"));
        Files.writeString(source.resolve("jars/broken.jar"), "not a zip");
        assertThat(evidence.inspect(source).unreadable()).isNotEmpty();
    }

    @Test void cancellationBeforeCommitRetainsPreviousProjectAndLeavesNoPublishedOutput() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(temporary.resolve("workspaces"));
        var session = translate(workflow, workflow.loadMod(source));
        Path project = session.workspace().resolve("project.ssmt.json");
        byte[] before = Files.readAllBytes(project);
        AtomicBoolean cancelled = new AtomicBoolean();
        assertThatThrownBy(() -> WorkflowOperation.run(cancelled::get, stage -> {
            if (stage.startsWith("Validating translations")) { cancelled.set(true); }
        }, () -> workflow.buildPatch(session, temporary.resolve("cancelled-output"))))
                .isInstanceOf(OperationCancelledException.class);
        assertThat(temporary.resolve("cancelled-output")).doesNotExist();
        assertThat(Files.readAllBytes(project)).isEqualTo(before);
        cancelled.set(false);
        assertThatThrownBy(() -> WorkflowOperation.run(cancelled::get, stage -> {
            if (stage.startsWith("Extracting")) { cancelled.set(true); }
        }, () -> workflow.loadMod(source))).isInstanceOf(OperationCancelledException.class);
        assertThat(Files.readAllBytes(project)).isEqualTo(before);
        workflow.buildPatch(session, temporary.resolve("after-cancellation"));
    }

    @Test void cancellationDuringPublicationCompletesSafelyAndClearsWorkerContext() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(temporary.resolve("workspaces"));
        var session = translate(workflow, workflow.loadMod(source));
        AtomicBoolean cancelled = new AtomicBoolean();
        Path output = temporary.resolve("published");
        WorkflowOperation.run(cancelled::get, stage -> {
            if (stage.startsWith("Publishing safely")) { cancelled.set(true); }
        }, () -> workflow.buildPatch(session, output));
        assertThat(Files.readString(output.resolve("data/strings/strings.json"))).contains("Translated Hello %s");
        assertThat(workflow.verifyInstalled(session, output)).isEmpty();
        WorkflowOperation.checkCancellation();
    }

    @Test void diagnosticExportIncludesOnlySelectedLogsAndRefusesSourceOrOverwrite() throws Exception {
        Path source = source();
        Path selected = temporary.resolve("selected.log");
        Files.writeString(selected, "selected diagnostic");
        Files.writeString(temporary.resolve("private.log"), "not selected");
        Path output = temporary.resolve("diagnostics.json");
        var export = new WorkflowDiagnosticExport();
        export.write(output, List.of(source), "Import", "Rejected response", List.of(selected));
        var report = json.readTree(output.toFile());
        assertThat(report.path("user_selected_logs").size()).isEqualTo(1);
        assertThat(report.path("user_selected_logs").get(0).path("name").asText()).isEqualTo("selected.log");
        assertThat(report.has("version")).isTrue();
        assertThat(report.has("commit")).isTrue();
        assertThat(Files.readString(output)).doesNotContain("private.log", "not selected");
        byte[] before = Files.readAllBytes(output);
        assertThatThrownBy(() -> export.write(output, List.of(source), "", "", List.of()))
                .isInstanceOf(ProjectException.class);
        assertThat(Files.readAllBytes(output)).isEqualTo(before);
        assertThatThrownBy(() -> export.write(source.resolve("diagnostic.json"), List.of(source), "", "", List.of()))
                .isInstanceOf(ProjectException.class).hasMessageContaining("outside the source");
        assertThat(source.resolve("diagnostic.json")).doesNotExist();
    }
}
