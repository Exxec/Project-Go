package com.ssmt.gui;

import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectBuildPreview;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.ProjectSourceDetails;
import com.ssmt.project.ReconciliationStatus;
import java.io.File;
import java.util.Optional;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

/** Project creation, refresh, preview and build actions for the advanced workspace. */
final class WorkspaceProjectActions {
    private final AdvancedWorkspace owner;

    WorkspaceProjectActions(AdvancedWorkspace owner) {
        this.owner = owner;
    }

    void openSampleProject(
            Stage stage, TableView<TranslationRow> table, Label status, Runnable onCompleted) {
        Optional<File> destination = owner.chooseDirectory(
                stage, GuiText.get("chooser.sampleWorkspace"));
        if (destination.isEmpty()) {
            return;
        }
        try {
            SyntheticSampleProject.InstalledSample sample =
                    SyntheticSampleProject.install(destination.orElseThrow().toPath());
            owner.workspace.create(
                    sample.sourceRoot(),
                    sample.projectFile(),
                    "ssmt.synthetic.sample.translation",
                    "Project Go Practice Translation");
            owner.refreshEditor(table);
            owner.updateStatus(status);
            owner.log(GuiText.get("status.sampleOpened"));
            onCompleted.run();
        } catch (java.io.IOException | ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Create sample project", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    void createProject(
            Stage stage,
            TableView<TranslationRow> table,
            Label status,
            AdvancedWorkspace.SchemaKind schemaKind,
            Runnable onCompleted) {
        Optional<File> source = owner.chooseDirectory(stage, GuiText.get("chooser.source"));
        if (source.isEmpty()) {
            return;
        }
        ProjectSourceDetails sourceDetails;
        try {
            sourceDetails = owner.workspace.inspectSource(source.orElseThrow().toPath());
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Load mod metadata", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        java.nio.file.Path destination;
        try {
            java.nio.file.Path artifactDirectory = AdvancedWorkspace.artifactDirectory(
                    source.orElseThrow().toPath(), null, sourceDetails.modName());
            java.nio.file.Files.createDirectories(artifactDirectory);
            destination = artifactDirectory.resolve(
                    AdvancedWorkspace.artifactBaseName(sourceDetails.modName()) + " project.ssmt.json");
        } catch (java.io.IOException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Load mod metadata", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        Optional<String> patchId = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("input.patchId.title"),
                GuiText.get("input.patchId.prompt"),
                sourceDetails.modId() + ".translation");
        if (patchId.isEmpty()) {
            return;
        }
        Optional<String> patchName = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("input.patchName.title"),
                GuiText.get("input.patchName.prompt"),
                sourceDetails.modName() + " " + GuiText.get("default.translatedName"));
        if (patchName.isEmpty()) {
            return;
        }
        Optional<File> schema = switch (schemaKind) {
            case JSON -> owner.chooseSchema(stage);
            case CSV -> owner.chooseCsvSchema(stage);
            case NONE -> Optional.empty();
        };
        if (schemaKind != AdvancedWorkspace.SchemaKind.NONE && schema.isEmpty()) {
            return;
        }
        owner.runBackgroundProjectAction(
                stage,
                GuiText.get("dialog.extracting"),
                GuiText.get("status.projectCreated"),
                cancellation -> {
            switch (schemaKind) {
                case JSON -> owner.workspace.createWithJsonSchema(
                        source.orElseThrow().toPath(),
                        destination,
                        patchId.orElseThrow(),
                        patchName.orElseThrow(),
                        schema.orElseThrow().toPath(),
                        cancellation);
                case CSV -> owner.workspace.createWithCsvSchema(
                        source.orElseThrow().toPath(),
                        destination,
                        patchId.orElseThrow(),
                        patchName.orElseThrow(),
                        schema.orElseThrow().toPath(),
                        cancellation);
                case NONE -> owner.workspace.create(
                        source.orElseThrow().toPath(),
                        destination,
                        patchId.orElseThrow(),
                        patchName.orElseThrow(),
                        cancellation);
            }
        }, () -> {
            owner.refreshEditor(table);
            owner.updateStatus(status);
            owner.workspace.lastRefreshResult().ifPresent(result -> {
                var changed = result.report().entries().stream()
                        .filter(entry -> entry.status() == ReconciliationStatus.CHANGED).toList();
                if (!changed.isEmpty()) {
                    changed.forEach(entry -> owner.editor.setSuggestions(
                            new TranslationRowId(entry.sourceFile(), entry.key()), entry.suggestions()));
                    String details = changed.stream()
                            .map(entry -> entry.sourceFile() + "#" + entry.key())
                            .collect(java.util.stream.Collectors.joining("\n"));
                    Alert review = new Alert(Alert.AlertType.INFORMATION,
                            GuiText.get("dialog.refresh.changedReview") + "\n\n" + details, ButtonType.OK);
                    review.setTitle(GuiText.get("dialog.refresh.title"));
                    review.showAndWait();
                }
            });
            onCompleted.run();
        });
    }

    void openProject(
            Stage stage, TableView<TranslationRow> table, Label status, Runnable onCompleted) {
        Optional<File> source = owner.chooseDirectory(stage, GuiText.get("chooser.source"));
        if (source.isEmpty()) {
            return;
        }
        File project =
                owner.projectChooser(GuiText.get("chooser.projectOpen")).showOpenDialog(stage);
        if (project == null) {
            return;
        }
        owner.runProjectAction("Open project", GuiText.get("status.projectOpened"), () -> {
            owner.workspace.open(source.orElseThrow().toPath(), project.toPath());
            owner.refreshEditor(table);
            owner.updateStatus(status);
            onCompleted.run();
        });
    }

    void buildProject(Stage stage, Label status) {
        Alert acknowledgment = new Alert(
                Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.permission.message"),
                ButtonType.YES,
                ButtonType.NO);
        acknowledgment.initOwner(stage);
        acknowledgment.setHeaderText(GuiText.get("dialog.permission.title"));
        if (acknowledgment.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        ProjectSourceDetails details;
        try {
            details = owner.workspace.inspectSource(owner.workspace.sourceRoot().orElseThrow());
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Build clone", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        java.nio.file.Path output = AdvancedWorkspace.artifactDirectory(
                owner.workspace.sourceRoot().orElseThrow(),
                owner.workspace.projectFile().orElse(null),
                details.modName()).resolve(
                        AdvancedWorkspace.artifactBaseName(details.modName()) + " translated");
        Task<ProjectBuildResult> task = new Task<>() {
            @Override
            protected ProjectBuildResult call() throws ProjectException {
                return owner.workspace.build(output, this::isCancelled);
            }
        };
        ProgressIndicator progress = new ProgressIndicator();
        progress.setAccessibleText(GuiText.get("accessible.buildProgress"));
        Alert progressDialog = new Alert(
                Alert.AlertType.INFORMATION,
                "",
                ButtonType.CANCEL);
        progressDialog.initOwner(stage);
        progressDialog.setHeaderText(GuiText.get("dialog.building"));
        progressDialog.getDialogPane().setContent(progress);
        progressDialog.setOnCloseRequest(ignored -> task.cancel());
        task.setOnSucceeded(ignored -> {
            progressDialog.close();
            ProjectBuildResult result = task.getValue();
            owner.updateStatus(status);
            owner.log(result.changed()
                    ? GuiText.get("status.patchPublished")
                    : GuiText.get("status.patchUnchanged"));
        });
        task.setOnCancelled(ignored -> {
            progressDialog.close();
            owner.log(GuiText.get("status.patchCancelled"));
        });
        task.setOnFailed(ignored -> {
            progressDialog.close();
            Throwable failure = task.getException();
            UserDiagnostic diagnostic = UserDiagnostic.failed("Build clone", failure);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        });
        Thread.ofVirtual().name("ssmt-patch-build").start(task);
        progressDialog.show();
    }

    void refreshProject(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        owner.runRefreshTask(stage, table, status, owner.workspace::previewRefresh);
    }

    void previewBuild(Stage stage) {
        owner.runProjectAction("Preview personal copy", "", () -> {
            ProjectBuildPreview preview = owner.workspace.previewBuild();
            Alert report = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
            report.initOwner(stage);
            report.setHeaderText(GuiText.get("dialog.previewBuild.title"));
            report.setContentText(GuiText.get("dialog.previewBuild.message")
                    .replace("{translated}", Integer.toString(preview.translatedEntries()))
                    .replace("{untranslated}", Integer.toString(preview.untranslatedEntries()))
                    .replace("{files}", Integer.toString(preview.sourceFiles())));
            report.showAndWait();
        });
    }

    void refreshProjectWithMemory(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        if (owner.activeTranslationMemory == null) {
            owner.openTranslationMemory(stage);
        }
        if (owner.activeTranslationMemory == null) {
            return;
        }
        Optional<String> sourceLanguage = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("dialog.sourceLanguage"),
                GuiText.get("dialog.sourceLanguage.prompt"),
                GuiText.get("default.sourceLanguage"));
        Optional<String> targetLanguage = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("dialog.targetLanguage"),
                GuiText.get("dialog.targetLanguage.prompt"),
                GuiText.get("default.targetLanguage"));
        if (sourceLanguage.isEmpty() || targetLanguage.isEmpty()) {
            return;
        }
        owner.runRefreshTask(
                stage,
                table,
                status,
                cancellation -> owner.workspace.previewRefreshWithTranslationMemory(
                        owner.activeTranslationMemory,
                        sourceLanguage.orElseThrow(),
                        targetLanguage.orElseThrow(),
                        0.8,
                        cancellation));
    }

    void runRefreshTask(
            Stage stage,
            TableView<TranslationRow> table,
            Label status,
            AdvancedWorkspace.RefreshAction action) {
        Task<ProjectRefreshResult> task = new Task<>() {
            @Override
            protected ProjectRefreshResult call() throws ProjectException {
                return action.run(this::isCancelled);
            }
        };
        ProgressIndicator progress = new ProgressIndicator();
        progress.setAccessibleText(GuiText.get("accessible.refreshProgress"));
        Alert dialog = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.CANCEL);
        dialog.initOwner(stage);
        dialog.setHeaderText(GuiText.get("dialog.refreshing"));
        dialog.getDialogPane().setContent(progress);
        dialog.setOnCloseRequest(ignored -> task.cancel());
        task.setOnSucceeded(ignored -> {
            dialog.close();
            try {
                owner.showRefreshResult(task.getValue(), table, status);
            } catch (ProjectException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Refresh project", exception);
                AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        task.setOnCancelled(ignored -> {
            dialog.close();
            owner.log(GuiText.get("status.refreshCancelled"));
        });
        task.setOnFailed(ignored -> {
            dialog.close();
            Throwable failure = task.getException();
            UserDiagnostic diagnostic = UserDiagnostic.failed("Refresh project", failure);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        });
        Thread.ofVirtual().name("ssmt-project-refresh").start(task);
        dialog.show();
    }

    void showRefreshResult(
            ProjectRefreshResult result,
            TableView<TranslationRow> table,
            Label status) throws ProjectException {
        result.report().entries().stream()
                .filter(entry -> !entry.suggestions().isEmpty())
                .forEach(entry -> owner.editor.setSuggestions(
                        new TranslationRowId(entry.sourceFile(), entry.key()),
                        entry.suggestions()));
        StringBuilder report =
                new StringBuilder(GuiText.get("dialog.refresh.report")).append('\n');
        for (ReconciliationStatus reconciliationStatus
                : ReconciliationStatus.values()) {
            report.append(reconciliationStatus)
                    .append(": ")
                    .append(result.report().count(reconciliationStatus))
                    .append('\n');
        }
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                report + "\n" + GuiText.get("dialog.refresh.apply"),
                ButtonType.YES,
                ButtonType.NO);
        confirmation.setTitle(GuiText.get("dialog.refresh.title"));
        confirmation.setHeaderText(GuiText.get("dialog.refresh.header"));
        if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            owner.workspace.applyRefresh(result);
            owner.refreshEditor(table);
            owner.updateStatus(status);
            owner.log(GuiText.get("status.refreshApplied"));
        } else {
            owner.log(GuiText.get("status.refreshDryRun"));
        }
    }
}
