package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/** Builds the existing editorTab view. */
final class TranslationEditorTab {
    private TranslationEditorTab() { }

    static Tab create(AdvancedWorkspace owner, Stage stage) {
        TableView<TranslationRow> table = new TableView<>();
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label(GuiText.get("placeholder.noProject")));

        TableColumn<TranslationRow, String> source = AdvancedWorkspace.column(
                GuiText.get("column.source"),
                row -> row.sourceText());
        TableColumn<TranslationRow, String> target = AdvancedWorkspace.column(
                GuiText.get("column.translation"),
                row -> row.translatedText());
        target.setCellFactory(TextFieldTableCell.forTableColumn());
        target.setOnEditCommit(event -> {
            owner.editor.updateTranslation(event.getRowValue().id(), event.getNewValue());
            owner.autosave();
            owner.refreshEditor(table);
        });
        TableColumn<TranslationRow, String> issues = AdvancedWorkspace.column(
                GuiText.get("column.validation"),
                row -> row.issues().isEmpty()
                        ? ""
                        : row.issues().stream()
                                .map(issue -> issue.message())
                                .reduce((left, right) -> left + "; " + right)
                                .orElse(""));
        TableColumn<TranslationRow, String> reviewed = AdvancedWorkspace.column(
                GuiText.get("column.reviewed"),
                row -> owner.editor.isReviewed(row.id()) ? GuiText.get("review.yes") : "");
        TableColumn<TranslationRow, String> provenance = AdvancedWorkspace.column(
                GuiText.get("column.provenance"),
                row -> owner.editor.provenance(row.id()).name());
        source.setPrefWidth(390);
        target.setPrefWidth(390);
        issues.setPrefWidth(300);
        table.getColumns().add(source);
        table.getColumns().add(target);
        table.getColumns().add(issues);
        table.getColumns().add(reviewed);
        table.getColumns().add(provenance);
        owner.refreshEditor(table);

        TextArea sourcePreview = AdvancedWorkspace.preview(GuiText.get("placeholder.sourcePreview"));
        TextArea targetPreview = AdvancedWorkspace.preview(GuiText.get("placeholder.targetPreview"));
        ComboBox<String> suggestions = new ComboBox<>();
        suggestions.setPromptText(GuiText.get("placeholder.suggestions"));
        suggestions.setAccessibleText(GuiText.get("accessible.suggestions"));
        table.getSelectionModel().selectedItemProperty().addListener(
                (ignored, oldRow, row) -> {
                    if (row != null) {
                        sourcePreview.setText(row.sourceText());
                        targetPreview.setText(row.translatedText());
                        suggestions.setItems(FXCollections.observableArrayList(
                                owner.editor.suggestions(row.id())));
                    }
                });
        SplitPane previews = new SplitPane(sourcePreview, targetPreview);
        previews.setDividerPositions(0.5);
        Label status = new Label(GuiText.get("status.noProject"));
        owner.editorTable = table;
        owner.editorStatus = status;
        owner.coverageLabel = new Label();
        Button save = new Button(GuiText.get("button.save"));
        save.setAccessibleText(GuiText.get("accessible.save"));
        save.setOnAction(ignored -> owner.runProjectAction(
                "Save", GuiText.get("status.projectSaved"), () -> {
            owner.workspace.save();
            owner.updateStatus(status);
        }));
        Button refresh = new Button(GuiText.get("button.refresh"));
        refresh.setAccessibleText(GuiText.get("accessible.refresh"));
        refresh.setOnAction(ignored -> owner.refreshProject(stage, table, status));
        Button refreshWithMemory = new Button(GuiText.get("button.refreshTm"));
        refreshWithMemory.setAccessibleText(
                GuiText.get("accessible.refreshTm"));
        refreshWithMemory.setOnAction(
                ignored -> owner.refreshProjectWithMemory(stage, table, status));
        Button openMemory = new Button(GuiText.get("button.openTm"));
        openMemory.setAccessibleText(GuiText.get("accessible.openTm"));
        openMemory.setOnAction(ignored -> owner.openTranslationMemory(stage));
        Button mergeMemory = new Button(GuiText.get("button.mergeTm"));
        mergeMemory.setAccessibleText(GuiText.get("accessible.mergeTm"));
        mergeMemory.setOnAction(ignored -> owner.compareAndMergeTranslationMemory(stage));
        Button build = new Button(GuiText.get("button.build"));
        build.setAccessibleText(GuiText.get("accessible.build"));
        build.setOnAction(ignored -> owner.buildProject(stage, status));
        Button previewBuild = new Button(GuiText.get("button.previewBuild"));
        previewBuild.setAccessibleText(GuiText.get("accessible.previewBuild"));
        previewBuild.setOnAction(ignored -> owner.previewBuild(stage));
        Button saveRestorePoint = new Button(GuiText.get("button.saveRestorePoint"));
        saveRestorePoint.setOnAction(ignored -> owner.runProjectAction(
                "Create restore point", GuiText.get("status.restorePointSaved"),
                owner.workspace::createRestorePoint));
        Button undoRestorePoint = new Button(GuiText.get("button.undoRestorePoint"));
        undoRestorePoint.setOnAction(ignored -> owner.runProjectAction(
                "Restore point", GuiText.get("status.restorePointRestored"), () -> {
                    if (!owner.workspace.restoreLastRestorePoint()) {
                        throw new ProjectException(GuiText.get("error.noRestorePoint"));
                    }
                    owner.refreshEditor(table);
                }));
        Button translateProject = new Button(GuiText.get("button.translateProject"));
        translateProject.setOnAction(ignored ->
                owner.translateProject(stage, table, status));
        Button resumeTranslation = new Button(GuiText.get("button.resumeTranslation"));
        resumeTranslation.setOnAction(ignored -> owner.runProjectAction(
                "Resume translation", GuiText.get("status.translationResumed"), () -> {
                    if (!owner.workspace.hasTranslationCheckpoint()) {
                        throw new ProjectException(GuiText.get("error.noTranslationCheckpoint"));
                    }
                    owner.workspace.resumeTranslationCheckpoint();
                    owner.refreshEditor(table);
                    owner.updateStatus(status);
                }));
        Button exportAi = new Button(GuiText.get("button.exportAi"));
        exportAi.setAccessibleText(GuiText.get("accessible.exportAi"));
        exportAi.setOnAction(ignored -> owner.exportAiPackage(stage));
        Button importAi = new Button(GuiText.get("button.importAi"));
        importAi.setAccessibleText(GuiText.get("accessible.importAi"));
        importAi.setOnAction(ignored -> owner.importAiResponse(stage, table, status));
        Button exportBrowserAi = new Button(GuiText.get("button.exportBrowserAi"));
        exportBrowserAi.setOnAction(ignored -> owner.exportBrowserReview(stage));
        Button openBrowserAi = new Button(GuiText.get("button.openBrowserAi"));
        openBrowserAi.setOnAction(ignored -> owner.openConfiguredAiWebsite(stage));
        Button copyBrowserPrompt = new Button(GuiText.get("button.copyBrowserPrompt"));
        copyBrowserPrompt.setOnAction(ignored -> owner.copyBrowserPrompt());
        Button openBrowserFolder = new Button(GuiText.get("button.openBrowserFolder"));
        openBrowserFolder.setOnAction(ignored -> owner.openLastBrowserExport());
        Button importBrowserAi = new Button(GuiText.get("button.importBrowserAi"));
        importBrowserAi.setOnAction(ignored -> owner.importBrowserReview(stage, table, status));
        Button reopenBrowserAi = new Button(GuiText.get("button.reopenBrowserAi"));
        reopenBrowserAi.setOnAction(ignored -> owner.reopenLastBrowserExport());
        TextField search = new TextField();
        search.setPromptText(GuiText.get("placeholder.search"));
        ComboBox<String> reviewStatus = new ComboBox<>();
        reviewStatus.getItems().addAll(
                GuiText.get("review.all"),
                TranslationStatus.UNTRANSLATED.name(),
                TranslationStatus.TRANSLATED.name(),
                TranslationStatus.INVALID.name());
        reviewStatus.getSelectionModel().selectFirst();
        ComboBox<String> provenanceFilter = new ComboBox<>();
        provenanceFilter.getItems().add(GuiText.get("review.allProvenance"));
        java.util.Arrays.stream(com.ssmt.core.model.TranslationProvenance.values())
                .map(Enum::name).forEach(provenanceFilter.getItems()::add);
        provenanceFilter.getSelectionModel().selectFirst();
        Runnable applyFilter = () -> table.setItems(FXCollections.observableArrayList(
                owner.editor.filter(
                        search.getText(),
                        GuiText.get("review.all").equals(reviewStatus.getValue())
                                ? Optional.empty()
                                : Optional.of(TranslationStatus.valueOf(
                                        reviewStatus.getValue()))).stream()
                        .filter(row -> GuiText.get("review.allProvenance")
                                        .equals(provenanceFilter.getValue())
                                || owner.editor.provenance(row.id()).name()
                                        .equals(provenanceFilter.getValue()))
                        .toList()));
        search.textProperty().addListener((ignored, oldValue, newValue) -> applyFilter.run());
        reviewStatus.valueProperty().addListener(
                (ignored, oldValue, newValue) -> applyFilter.run());
        provenanceFilter.valueProperty().addListener(
                (ignored, oldValue, newValue) -> applyFilter.run());
        Button markReviewed = new Button(GuiText.get("button.review"));
        markReviewed.setAccessibleText(GuiText.get("accessible.review"));
        markReviewed.setOnAction(ignored -> {
            owner.editor.markReviewed(table.getSelectionModel().getSelectedItems().stream()
                    .map(TranslationRow::id)
                    .toList());
            owner.refreshEditor(table);
        });
        Button approveDraft = new Button(GuiText.get("button.approveDraft"));
        approveDraft.setOnAction(ignored -> owner.runProjectAction(
                "Approve draft", GuiText.get("status.draftsApproved"), () -> {
                    owner.editor.approve(table.getSelectionModel().getSelectedItems().stream()
                            .map(TranslationRow::id).toList());
                    owner.workspace.save();
                    owner.refreshEditor(table);
                }));
        Button rejectDraft = new Button(GuiText.get("button.rejectDraft"));
        rejectDraft.setOnAction(ignored -> owner.runProjectAction(
                "Reject draft", GuiText.get("status.draftsRejected"), () -> {
                    owner.editor.reject(table.getSelectionModel().getSelectedItems().stream()
                            .map(TranslationRow::id).toList());
                    owner.workspace.save();
                    owner.refreshEditor(table);
                }));
        Button inspectEvidence = new Button(GuiText.get("button.inspectEvidence"));
        inspectEvidence.setOnAction(ignored -> {
            TranslationRow row = table.getSelectionModel().getSelectedItem();
            if (row == null) {
                AdvancedWorkspace.showError(GuiText.get("error.routingEvidence"),
                        GuiText.get("error.noRowSelected"));
                return;
            }
            var request = new com.ssmt.ai.AiTranslationRequest(
                    row.sourceText(), "und", "en", row.id().toString(), "");
            List<String> evidence = new com.ssmt.ai.RoutingEvidenceDetector().inspect(
                    request, row.translatedText());
            Alert report = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
            report.setHeaderText(GuiText.get("dialog.routingEvidence.title"));
            report.setContentText(evidence.isEmpty()
                    ? GuiText.get("status.noRoutingEvidence")
                    : String.join(System.lineSeparator(), evidence));
            report.showAndWait();
        });
        Button viewLineage = new Button(GuiText.get("button.viewLineage"));
        viewLineage.setOnAction(ignored -> {
            TranslationRow row = table.getSelectionModel().getSelectedItem();
            if (row == null) {
                AdvancedWorkspace.showError(GuiText.get("error.lineage"), GuiText.get("error.noRowSelected"));
                return;
            }
            try {
                String detail = owner.workspace.generationMetadata(row.id())
                        .map(Object::toString)
                        .orElse(GuiText.get("status.noLineage"));
                Alert report = new Alert(Alert.AlertType.INFORMATION, detail, ButtonType.OK);
                report.setHeaderText(GuiText.get("dialog.lineage.title"));
                report.showAndWait();
            } catch (ProjectException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("View lineage", exception);
                AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        Button auditGlossary = new Button(GuiText.get("button.auditGlossary"));
        auditGlossary.setOnAction(ignored -> owner.auditGlossary(stage));
        Button exportReport = new Button(GuiText.get("button.exportTranslationReport"));
        exportReport.setOnAction(ignored -> owner.exportTranslationReport(stage));
        Button applySuggestion = new Button(GuiText.get("button.applySuggestion"));
        applySuggestion.setAccessibleText(GuiText.get("accessible.applySuggestion"));
        applySuggestion.setOnAction(ignored -> {
            TranslationRow row = table.getSelectionModel().getSelectedItem();
            int suggestionIndex = suggestions.getSelectionModel().getSelectedIndex();
            if (row != null && suggestionIndex >= 0) {
                owner.editor.applySuggestion(row.id(), suggestionIndex);
                owner.autosave();
                owner.refreshEditor(table);
            }
        });
        table.setOnKeyPressed(event -> {
            if (!event.isControlDown()) {
                return;
            }
            if (event.getCode() == KeyCode.S) {
                owner.runProjectAction("Save", GuiText.get("status.projectSaved"), owner.workspace::save);
                event.consume();
            } else if (event.getCode() == KeyCode.F) {
                search.requestFocus();
                event.consume();
            }
        });
        MenuButton moreActions = new MenuButton(GuiText.get("button.moreActions"));
        moreActions.setAccessibleText(GuiText.get("accessible.moreActions"));
        Menu projectTools = new Menu(GuiText.get("menu.projectTools"));
        projectTools.getItems().addAll(
                AdvancedWorkspace.menuItem(GuiText.get("button.previewBuild"), previewBuild),
                AdvancedWorkspace.menuItem(GuiText.get("button.saveRestorePoint"), saveRestorePoint),
                AdvancedWorkspace.menuItem(GuiText.get("button.undoRestorePoint"), undoRestorePoint),
                AdvancedWorkspace.menuItem(GuiText.get("button.refreshTm"), refreshWithMemory),
                AdvancedWorkspace.menuItem(GuiText.get("button.translateProject"), translateProject),
                AdvancedWorkspace.menuItem(GuiText.get("button.resumeTranslation"), resumeTranslation));
        Menu catalogTools = new Menu(GuiText.get("menu.catalogTools"));
        catalogTools.getItems().addAll(
                AdvancedWorkspace.menuItem(GuiText.get("button.openTm"), openMemory),
                AdvancedWorkspace.menuItem(GuiText.get("button.mergeTm"), mergeMemory),
                AdvancedWorkspace.menuItem(GuiText.get("button.auditGlossary"), auditGlossary),
                AdvancedWorkspace.menuItem(GuiText.get("button.exportTranslationReport"), exportReport));
        Menu aiTools = new Menu(GuiText.get("menu.aiTools"));
        aiTools.getItems().addAll(
                AdvancedWorkspace.menuItem(GuiText.get("button.exportAi"), exportAi),
                AdvancedWorkspace.menuItem(GuiText.get("button.importAi"), importAi));
        Menu browserReview = new Menu(GuiText.get("menu.browserReview"));
        browserReview.getItems().addAll(
                AdvancedWorkspace.menuItem(GuiText.get("button.exportBrowserAi"), exportBrowserAi),
                AdvancedWorkspace.menuItem(GuiText.get("button.openBrowserAi"), openBrowserAi),
                AdvancedWorkspace.menuItem(GuiText.get("button.copyBrowserPrompt"), copyBrowserPrompt),
                AdvancedWorkspace.menuItem(GuiText.get("button.openBrowserFolder"), openBrowserFolder),
                AdvancedWorkspace.menuItem(GuiText.get("button.importBrowserAi"), importBrowserAi),
                AdvancedWorkspace.menuItem(GuiText.get("button.reopenBrowserAi"), reopenBrowserAi));
        moreActions.getItems().addAll(projectTools, catalogTools, aiTools, browserReview);
        MenuButton selectedRowActions = new MenuButton(GuiText.get("button.selectedRowActions"));
        selectedRowActions.setAccessibleText(GuiText.get("accessible.selectedRowActions"));
        selectedRowActions.getItems().addAll(
                AdvancedWorkspace.menuItem(GuiText.get("button.review"), markReviewed),
                AdvancedWorkspace.menuItem(GuiText.get("button.approveDraft"), approveDraft),
                AdvancedWorkspace.menuItem(GuiText.get("button.rejectDraft"), rejectDraft),
                AdvancedWorkspace.menuItem(GuiText.get("button.inspectEvidence"), inspectEvidence),
                AdvancedWorkspace.menuItem(GuiText.get("button.viewLineage"), viewLineage));
        Label workspaceHeading = new Label(GuiText.get("heading.translationWorkspace"));
        workspaceHeading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        HBox toolbarProject =
                new HBox(8, workspaceHeading, save, refresh, previewBuild, build, moreActions);
        HBox toolbarStatus = new HBox(8, status, owner.coverageLabel);
        VBox toolbar = new VBox(4, toolbarProject, toolbarStatus);
        HBox filters =
                new HBox(8, new Label(GuiText.get("label.filter")), search, reviewStatus,
                        provenanceFilter, selectedRowActions);
        HBox suggestionControls = new HBox(8, suggestions, applySuggestion);
        VBox content =
                new VBox(8, toolbar, filters, table, suggestionControls, previews);
        table.prefHeightProperty().bind(content.heightProperty().multiply(0.7));
        owner.updateCoverage();
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.translation"), content);
    }
}
