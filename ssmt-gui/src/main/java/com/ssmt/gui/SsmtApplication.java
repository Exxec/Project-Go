package com.ssmt.gui;

import com.ssmt.ai.AiProviderType;
import com.ssmt.ai.AiProviderLocation;
import com.ssmt.ai.AiProviderSettings;
import com.ssmt.ai.ArgosDevice;
import com.ssmt.ai.ArgosTranslatePlugin;
import com.ssmt.ai.OfflineTranslationChain;
import com.ssmt.ai.TranslateLocallyPlugin;
import com.ssmt.ai.TranslationMode;
import com.ssmt.ai.TranslationResourceLimits;
import com.ssmt.project.ProjectBuildResult;
import com.ssmt.project.ProjectBuildPreview;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.project.AiTranslationImportResult;
import com.ssmt.project.AiImportPolicy;
import com.ssmt.project.ProjectSourceDetails;
import com.ssmt.project.ReconciliationStatus;
import com.ssmt.project.ChainedProjectTranslationEngine;
import com.ssmt.project.PreferredLocalProvider;
import com.ssmt.project.ProjectTranslationSettings;
import com.ssmt.project.TranslationMemoryApprovedGlossary;
import com.ssmt.tm.TranslationMemoryMergeResult;
import com.ssmt.tm.SqliteTranslationMemory;
import java.io.File;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * JavaFX desktop shell for translation and diagnostic view models.
 */
public final class SsmtApplication extends Application {
    public static final String WINDOW_TITLE = GuiText.get("window.title");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String MEMORY_PREFERENCE = "translationMemoryPath";
    private static final String AI_GUIDANCE_PREFERENCE = "aiExchangeGuidanceShown";
    private static final String BROWSER_AI_NAME = "browserAiProviderName";
    private static final String BROWSER_AI_URL = "browserAiProviderUrl";
    private static final String BROWSER_AI_EXPORT = "browserAiLastExport";
    private static final String PROVIDER_TYPE = "configuredProviderType";
    private static final String PROVIDER_ENDPOINT = "configuredProviderEndpoint";
    private static final String PROVIDER_MODEL = "configuredProviderModel";
    private static final String PROVIDER_CREDENTIAL = "configuredProviderCredentialEnv";

    private final LogDashboardViewModel logModel = new LogDashboardViewModel(1_000);
    private final TranslationEditorController editor = new TranslationEditorController();
    private final ProjectWorkspaceController workspace =
            new ProjectWorkspaceController(editor);
    private Path activeTranslationMemory;
    private TableView<LogEntry> logTable;
    private Label coverageLabel;
    private TableView<com.ssmt.project.FontCoverageFinding> fontCoverageTable;
    private TabPane navigationTabs;
    private Tab editorNavigationTab;
    private TableView<TranslationRow> editorTable;
    private Label editorStatus;

    /**
     * Launches JavaFX.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        initializeTranslationMemory();
        editorNavigationTab = editorTab(stage);
        navigationTabs = new TabPane(
                welcomeTab(stage),
                editorNavigationTab,
                toolsTab(stage));
        BorderPane root = new BorderPane(navigationTabs);
        root.setPadding(new Insets(8));
        stage.setTitle(WINDOW_TITLE);
        stage.getIcons().add(new Image(java.util.Objects.requireNonNull(
                        SsmtApplication.class.getResource("ssmt-icon.png"))
                .toExternalForm()));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(new Scene(root, 1200, 760));
        stage.setOnCloseRequest(event -> {
            if (!workspace.hasUnsavedChanges()) {
                return;
            }
            Alert warning = new Alert(
                    Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.unsaved.message"),
                    ButtonType.YES,
                    ButtonType.NO);
            warning.setHeaderText(GuiText.get("dialog.unsaved.title"));
            if (warning.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                event.consume();
            }
        });
        stage.show();
        showFirstRunGuidance(stage);
    }

    /**
     * The intentional first screen: the three actions a new user needs before
     * encountering the editor and its optional tools.
     */
    private Tab welcomeTab(Stage stage) {
        Label title = new Label(GuiText.get("heading.welcome"));
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label introduction = new Label(GuiText.get("label.welcomeIntroduction"));
        introduction.setWrapText(true);

        SplitMenuButton start = new SplitMenuButton();
        start.setText(GuiText.get("button.create"));
        start.setAccessibleText(GuiText.get("accessible.create"));
        start.setOnAction(ignored -> createProject(
                stage, editorTable, editorStatus, SchemaKind.NONE, this::showEditor));
        start.getItems().addAll(
                schemaVariantMenuItem(
                        GuiText.get("button.createSchema"),
                        GuiText.get("accessible.createSchema"),
                        ignored -> createProject(
                                stage, editorTable, editorStatus, SchemaKind.JSON, this::showEditor)),
                schemaVariantMenuItem(
                        GuiText.get("button.createCsvSchema"),
                        GuiText.get("accessible.createCsvSchema"),
                        ignored -> createProject(
                                stage, editorTable, editorStatus, SchemaKind.CSV, this::showEditor)));
        Button open = new Button(GuiText.get("button.open"));
        open.setAccessibleText(GuiText.get("accessible.open"));
        open.setOnAction(ignored -> openProject(
                stage, editorTable, editorStatus, this::showEditor));
        Button sample = new Button(GuiText.get("button.sample"));
        sample.setAccessibleText(GuiText.get("accessible.sample"));
        sample.setOnAction(ignored -> openSampleProject(
                stage, editorTable, editorStatus, this::showEditor));
        HBox projectActions = new HBox(10, start, open, sample);

        Label steps = new Label(GuiText.get("heading.nextSteps"));
        steps.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        VBox journey = new VBox(8,
                new Label(GuiText.get("label.stepOne")),
                new Label(GuiText.get("label.stepTwo")),
                new Label(GuiText.get("label.stepThree")));
        journey.setStyle("-fx-padding: 12px; -fx-background-color: #f4f6f8;"
                + " -fx-background-radius: 6px;");

        Label reassurance = new Label(GuiText.get("label.sourceSafety"));
        reassurance.setWrapText(true);
        VBox content = new VBox(16, title, introduction, projectActions, steps, journey, reassurance);
        content.setPadding(new Insets(28));
        return fixedTab(GuiText.get("tab.start"), content);
    }

    /** Keeps infrequently used configuration and diagnostics reachable without competing with work. */
    private Tab toolsTab(Stage stage) {
        TabPane tools = new TabPane(
                projectInfoTab(),
                schemaEditorTab(stage),
                providerSettingsTab(),
                fontCoverageTab(stage),
                logTab());
        return fixedTab(GuiText.get("tab.tools"), tools);
    }

    private void showEditor() {
        navigationTabs.getSelectionModel().select(editorNavigationTab);
    }

    private Tab editorTab(Stage stage) {
        TableView<TranslationRow> table = new TableView<>();
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label(GuiText.get("placeholder.noProject")));

        TableColumn<TranslationRow, String> source = column(
                GuiText.get("column.source"),
                row -> row.sourceText());
        TableColumn<TranslationRow, String> target = column(
                GuiText.get("column.translation"),
                row -> row.translatedText());
        target.setCellFactory(TextFieldTableCell.forTableColumn());
        target.setOnEditCommit(event -> {
            editor.updateTranslation(event.getRowValue().id(), event.getNewValue());
            autosave();
            refreshEditor(table);
        });
        TableColumn<TranslationRow, String> issues = column(
                GuiText.get("column.validation"),
                row -> row.issues().isEmpty()
                        ? ""
                        : row.issues().stream()
                                .map(issue -> issue.message())
                                .reduce((left, right) -> left + "; " + right)
                                .orElse(""));
        TableColumn<TranslationRow, String> reviewed = column(
                GuiText.get("column.reviewed"),
                row -> editor.isReviewed(row.id()) ? GuiText.get("review.yes") : "");
        TableColumn<TranslationRow, String> provenance = column(
                GuiText.get("column.provenance"),
                row -> editor.provenance(row.id()).name());
        source.setPrefWidth(390);
        target.setPrefWidth(390);
        issues.setPrefWidth(300);
        table.getColumns().add(source);
        table.getColumns().add(target);
        table.getColumns().add(issues);
        table.getColumns().add(reviewed);
        table.getColumns().add(provenance);
        refreshEditor(table);

        TextArea sourcePreview = preview(GuiText.get("placeholder.sourcePreview"));
        TextArea targetPreview = preview(GuiText.get("placeholder.targetPreview"));
        ComboBox<String> suggestions = new ComboBox<>();
        suggestions.setPromptText(GuiText.get("placeholder.suggestions"));
        suggestions.setAccessibleText(GuiText.get("accessible.suggestions"));
        table.getSelectionModel().selectedItemProperty().addListener(
                (ignored, oldRow, row) -> {
                    if (row != null) {
                        sourcePreview.setText(row.sourceText());
                        targetPreview.setText(row.translatedText());
                        suggestions.setItems(FXCollections.observableArrayList(
                                editor.suggestions(row.id())));
                    }
                });
        SplitPane previews = new SplitPane(sourcePreview, targetPreview);
        previews.setDividerPositions(0.5);
        Label status = new Label(GuiText.get("status.noProject"));
        editorTable = table;
        editorStatus = status;
        coverageLabel = new Label();
        Button save = new Button(GuiText.get("button.save"));
        save.setAccessibleText(GuiText.get("accessible.save"));
        save.setOnAction(ignored -> runProjectAction(
                "Save", GuiText.get("status.projectSaved"), () -> {
            workspace.save();
            updateStatus(status);
        }));
        Button refresh = new Button(GuiText.get("button.refresh"));
        refresh.setAccessibleText(GuiText.get("accessible.refresh"));
        refresh.setOnAction(ignored -> refreshProject(stage, table, status));
        Button refreshWithMemory = new Button(GuiText.get("button.refreshTm"));
        refreshWithMemory.setAccessibleText(
                GuiText.get("accessible.refreshTm"));
        refreshWithMemory.setOnAction(
                ignored -> refreshProjectWithMemory(stage, table, status));
        Button openMemory = new Button(GuiText.get("button.openTm"));
        openMemory.setAccessibleText(GuiText.get("accessible.openTm"));
        openMemory.setOnAction(ignored -> openTranslationMemory(stage));
        Button mergeMemory = new Button(GuiText.get("button.mergeTm"));
        mergeMemory.setAccessibleText(GuiText.get("accessible.mergeTm"));
        mergeMemory.setOnAction(ignored -> compareAndMergeTranslationMemory(stage));
        Button build = new Button(GuiText.get("button.build"));
        build.setAccessibleText(GuiText.get("accessible.build"));
        build.setOnAction(ignored -> buildProject(stage, status));
        Button previewBuild = new Button(GuiText.get("button.previewBuild"));
        previewBuild.setAccessibleText(GuiText.get("accessible.previewBuild"));
        previewBuild.setOnAction(ignored -> previewBuild(stage));
        Button saveRestorePoint = new Button(GuiText.get("button.saveRestorePoint"));
        saveRestorePoint.setOnAction(ignored -> runProjectAction(
                "Create restore point", GuiText.get("status.restorePointSaved"),
                workspace::createRestorePoint));
        Button undoRestorePoint = new Button(GuiText.get("button.undoRestorePoint"));
        undoRestorePoint.setOnAction(ignored -> runProjectAction(
                "Restore point", GuiText.get("status.restorePointRestored"), () -> {
                    if (!workspace.restoreLastRestorePoint()) {
                        throw new ProjectException(GuiText.get("error.noRestorePoint"));
                    }
                    refreshEditor(table);
                }));
        Button translateProject = new Button(GuiText.get("button.translateProject"));
        translateProject.setOnAction(ignored ->
                translateProject(stage, table, status));
        Button resumeTranslation = new Button(GuiText.get("button.resumeTranslation"));
        resumeTranslation.setOnAction(ignored -> runProjectAction(
                "Resume translation", GuiText.get("status.translationResumed"), () -> {
                    if (!workspace.hasTranslationCheckpoint()) {
                        throw new ProjectException(GuiText.get("error.noTranslationCheckpoint"));
                    }
                    workspace.resumeTranslationCheckpoint();
                    refreshEditor(table);
                    updateStatus(status);
                }));
        Button exportAi = new Button(GuiText.get("button.exportAi"));
        exportAi.setAccessibleText(GuiText.get("accessible.exportAi"));
        exportAi.setOnAction(ignored -> exportAiPackage(stage));
        Button importAi = new Button(GuiText.get("button.importAi"));
        importAi.setAccessibleText(GuiText.get("accessible.importAi"));
        importAi.setOnAction(ignored -> importAiResponse(stage, table, status));
        Button exportBrowserAi = new Button(GuiText.get("button.exportBrowserAi"));
        exportBrowserAi.setOnAction(ignored -> exportBrowserReview(stage));
        Button openBrowserAi = new Button(GuiText.get("button.openBrowserAi"));
        openBrowserAi.setOnAction(ignored -> openConfiguredAiWebsite(stage));
        Button copyBrowserPrompt = new Button(GuiText.get("button.copyBrowserPrompt"));
        copyBrowserPrompt.setOnAction(ignored -> copyBrowserPrompt());
        Button openBrowserFolder = new Button(GuiText.get("button.openBrowserFolder"));
        openBrowserFolder.setOnAction(ignored -> openLastBrowserExport());
        Button importBrowserAi = new Button(GuiText.get("button.importBrowserAi"));
        importBrowserAi.setOnAction(ignored -> importBrowserReview(stage, table, status));
        Button reopenBrowserAi = new Button(GuiText.get("button.reopenBrowserAi"));
        reopenBrowserAi.setOnAction(ignored -> reopenLastBrowserExport());
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
                editor.filter(
                        search.getText(),
                        GuiText.get("review.all").equals(reviewStatus.getValue())
                                ? Optional.empty()
                                : Optional.of(TranslationStatus.valueOf(
                                        reviewStatus.getValue()))).stream()
                        .filter(row -> GuiText.get("review.allProvenance")
                                        .equals(provenanceFilter.getValue())
                                || editor.provenance(row.id()).name()
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
            editor.markReviewed(table.getSelectionModel().getSelectedItems().stream()
                    .map(TranslationRow::id)
                    .toList());
            refreshEditor(table);
        });
        Button approveDraft = new Button(GuiText.get("button.approveDraft"));
        approveDraft.setOnAction(ignored -> runProjectAction(
                "Approve draft", GuiText.get("status.draftsApproved"), () -> {
                    editor.approve(table.getSelectionModel().getSelectedItems().stream()
                            .map(TranslationRow::id).toList());
                    workspace.save();
                    refreshEditor(table);
                }));
        Button rejectDraft = new Button(GuiText.get("button.rejectDraft"));
        rejectDraft.setOnAction(ignored -> runProjectAction(
                "Reject draft", GuiText.get("status.draftsRejected"), () -> {
                    editor.reject(table.getSelectionModel().getSelectedItems().stream()
                            .map(TranslationRow::id).toList());
                    workspace.save();
                    refreshEditor(table);
                }));
        Button inspectEvidence = new Button(GuiText.get("button.inspectEvidence"));
        inspectEvidence.setOnAction(ignored -> {
            TranslationRow row = table.getSelectionModel().getSelectedItem();
            if (row == null) {
                showError(GuiText.get("error.routingEvidence"),
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
                showError(GuiText.get("error.lineage"), GuiText.get("error.noRowSelected"));
                return;
            }
            try {
                String detail = workspace.generationMetadata(row.id())
                        .map(Object::toString)
                        .orElse(GuiText.get("status.noLineage"));
                Alert report = new Alert(Alert.AlertType.INFORMATION, detail, ButtonType.OK);
                report.setHeaderText(GuiText.get("dialog.lineage.title"));
                report.showAndWait();
            } catch (ProjectException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("View lineage", exception);
                showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        Button auditGlossary = new Button(GuiText.get("button.auditGlossary"));
        auditGlossary.setOnAction(ignored -> auditGlossary(stage));
        Button exportReport = new Button(GuiText.get("button.exportTranslationReport"));
        exportReport.setOnAction(ignored -> exportTranslationReport(stage));
        Button applySuggestion = new Button(GuiText.get("button.applySuggestion"));
        applySuggestion.setAccessibleText(GuiText.get("accessible.applySuggestion"));
        applySuggestion.setOnAction(ignored -> {
            TranslationRow row = table.getSelectionModel().getSelectedItem();
            int suggestionIndex = suggestions.getSelectionModel().getSelectedIndex();
            if (row != null && suggestionIndex >= 0) {
                editor.applySuggestion(row.id(), suggestionIndex);
                autosave();
                refreshEditor(table);
            }
        });
        table.setOnKeyPressed(event -> {
            if (!event.isControlDown()) {
                return;
            }
            if (event.getCode() == KeyCode.S) {
                runProjectAction("Save", GuiText.get("status.projectSaved"), workspace::save);
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
                menuItem(GuiText.get("button.previewBuild"), previewBuild),
                menuItem(GuiText.get("button.saveRestorePoint"), saveRestorePoint),
                menuItem(GuiText.get("button.undoRestorePoint"), undoRestorePoint),
                menuItem(GuiText.get("button.refreshTm"), refreshWithMemory),
                menuItem(GuiText.get("button.translateProject"), translateProject),
                menuItem(GuiText.get("button.resumeTranslation"), resumeTranslation));
        Menu catalogTools = new Menu(GuiText.get("menu.catalogTools"));
        catalogTools.getItems().addAll(
                menuItem(GuiText.get("button.openTm"), openMemory),
                menuItem(GuiText.get("button.mergeTm"), mergeMemory),
                menuItem(GuiText.get("button.auditGlossary"), auditGlossary),
                menuItem(GuiText.get("button.exportTranslationReport"), exportReport));
        Menu aiTools = new Menu(GuiText.get("menu.aiTools"));
        aiTools.getItems().addAll(
                menuItem(GuiText.get("button.exportAi"), exportAi),
                menuItem(GuiText.get("button.importAi"), importAi));
        Menu browserReview = new Menu(GuiText.get("menu.browserReview"));
        browserReview.getItems().addAll(
                menuItem(GuiText.get("button.exportBrowserAi"), exportBrowserAi),
                menuItem(GuiText.get("button.openBrowserAi"), openBrowserAi),
                menuItem(GuiText.get("button.copyBrowserPrompt"), copyBrowserPrompt),
                menuItem(GuiText.get("button.openBrowserFolder"), openBrowserFolder),
                menuItem(GuiText.get("button.importBrowserAi"), importBrowserAi),
                menuItem(GuiText.get("button.reopenBrowserAi"), reopenBrowserAi));
        moreActions.getItems().addAll(projectTools, catalogTools, aiTools, browserReview);
        MenuButton selectedRowActions = new MenuButton(GuiText.get("button.selectedRowActions"));
        selectedRowActions.setAccessibleText(GuiText.get("accessible.selectedRowActions"));
        selectedRowActions.getItems().addAll(
                menuItem(GuiText.get("button.review"), markReviewed),
                menuItem(GuiText.get("button.approveDraft"), approveDraft),
                menuItem(GuiText.get("button.rejectDraft"), rejectDraft),
                menuItem(GuiText.get("button.inspectEvidence"), inspectEvidence),
                menuItem(GuiText.get("button.viewLineage"), viewLineage));
        Label workspaceHeading = new Label(GuiText.get("heading.translationWorkspace"));
        workspaceHeading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        HBox toolbarProject =
                new HBox(8, workspaceHeading, save, refresh, previewBuild, build, moreActions);
        HBox toolbarStatus = new HBox(8, status, coverageLabel);
        VBox toolbar = new VBox(4, toolbarProject, toolbarStatus);
        HBox filters =
                new HBox(8, new Label(GuiText.get("label.filter")), search, reviewStatus,
                        provenanceFilter, selectedRowActions);
        HBox suggestionControls = new HBox(8, suggestions, applySuggestion);
        VBox content =
                new VBox(8, toolbar, filters, table, suggestionControls, previews);
        table.prefHeightProperty().bind(content.heightProperty().multiply(0.7));
        updateCoverage();
        return fixedTab(GuiText.get("tab.translation"), content);
    }

    private void auditGlossary(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.glossary"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.json"), "*.json"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            var findings = workspace.auditGlossary(selected.toPath());
            String detail = findings.isEmpty()
                    ? GuiText.get("status.noGlossaryConflicts")
                    : findings.stream().map(finding -> "%s#%s: %s -> %s".formatted(
                            finding.entry().sourceFile(), finding.entry().key(),
                            finding.term().source(), finding.term().target()))
                            .reduce((left, right) -> left + System.lineSeparator() + right)
                            .orElseThrow();
            Alert report = new Alert(Alert.AlertType.INFORMATION, detail, ButtonType.OK);
            report.setHeaderText(GuiText.get("dialog.glossaryAudit.title"));
            report.showAndWait();
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Glossary audit", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private void exportTranslationReport(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.translationReport"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.csv"), "*.csv"));
        File selected = chooser.showSaveDialog(stage);
        if (selected != null) {
            runProjectAction("Export translation report", GuiText.get("status.translationReportExported"),
                    () -> workspace.exportTranslationReport(selected.toPath()));
        }
    }

    private enum SchemaKind { NONE, JSON, CSV }

    private void openSampleProject(
            Stage stage, TableView<TranslationRow> table, Label status, Runnable onCompleted) {
        Optional<File> destination = chooseDirectory(
                stage, GuiText.get("chooser.sampleWorkspace"));
        if (destination.isEmpty()) {
            return;
        }
        try {
            SyntheticSampleProject.InstalledSample sample =
                    SyntheticSampleProject.install(destination.orElseThrow().toPath());
            workspace.create(
                    sample.sourceRoot(),
                    sample.projectFile(),
                    "ssmt.synthetic.sample.translation",
                    "Project Go Practice Translation");
            refreshEditor(table);
            updateStatus(status);
            log(GuiText.get("status.sampleOpened"));
            onCompleted.run();
        } catch (java.io.IOException | ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Create sample project", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private Tab projectInfoTab() {
        VBox locations = new VBox(6);
        Label workflow = new Label();
        workflow.setWrapText(true);
        Runnable refreshInfo = () -> {
            locations.getChildren().clear();
            ProjectWorkspaceInfo info = workspace.workspaceInfo(activeTranslationMemory);
            addPathRow(locations, GuiText.get("label.sourceRoot"), info.sourceRoot());
            addPathRow(locations, GuiText.get("label.projectFile"), info.projectFile());
            addPathRow(locations, GuiText.get("label.outputRoot"), info.outputRoot());
            addPathRow(locations, GuiText.get("label.sourceBackupRoot"),
                    info.sourceBackupRoot());
            addPathRow(locations, GuiText.get("label.translationMemory"),
                    info.translationMemory());
            addPathRow(locations, GuiText.get("label.recoveryRoot"), info.recoveryRoot());
            addPathRow(locations, GuiText.get("label.jsonSchema"), info.jsonSchemaCatalog());
            addPathRow(locations, GuiText.get("label.csvSchema"), info.csvSchemaCatalog());
            workflow.setText(workflowSummary(info));
        };
        Button refreshButton = new Button(GuiText.get("button.refreshInfo"));
        refreshButton.setOnAction(ignored -> refreshInfo.run());
        refreshInfo.run();
        VBox content = new VBox(
                10,
                new Label(GuiText.get("heading.workflow")),
                workflow,
                new Label(GuiText.get("heading.locations")),
                locations,
                refreshButton);
        return fixedTab(GuiText.get("tab.projectInfo"), content);
    }

    private void addPathRow(VBox parent, String name, Optional<Path> path) {
        Label value = new Label(path.map(Path::toString)
                .orElse(GuiText.get("status.notSet")));
        value.setWrapText(true);
        Button reveal = new Button(GuiText.get("button.openFolder"));
        reveal.setDisable(path.isEmpty() || !java.nio.file.Files.exists(path.orElseThrow()));
        reveal.setOnAction(ignored -> path.ifPresent(this::revealPath));
        parent.getChildren().add(new HBox(8, new Label(name), value, reveal));
    }

    private void revealPath(Path path) {
        Path target = java.nio.file.Files.isDirectory(path) ? path : path.getParent();
        if (target == null || !java.nio.file.Files.isDirectory(target)) {
            return;
        }
        getHostServices().showDocument(target.toUri().toString());
    }

    private String workflowSummary(ProjectWorkspaceInfo info) {
        String open = info.projectFile().isPresent() ? "✓" : "○";
        String translated = "○";
        if (info.projectFile().isPresent()) {
            try {
                var coverage = workspace.translationCoverage();
                translated = coverage.translatedEntries() == coverage.totalEntries()
                        ? "✓" : "◐";
            } catch (ProjectException ignored) {
                translated = "○";
            }
        }
        String built = info.outputRoot().isPresent() ? "✓" : "○";
        return GuiText.get("status.workflow")
                .replace("{open}", open)
                .replace("{translated}", translated)
                .replace("{built}", built);
    }

    private void createProject(
            Stage stage,
            TableView<TranslationRow> table,
            Label status,
            SchemaKind schemaKind,
            Runnable onCompleted) {
        Optional<File> source = chooseDirectory(stage, GuiText.get("chooser.source"));
        if (source.isEmpty()) {
            return;
        }
        ProjectSourceDetails sourceDetails;
        try {
            sourceDetails = workspace.inspectSource(source.orElseThrow().toPath());
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Load mod metadata", exception);
            showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        java.nio.file.Path destination;
        try {
            java.nio.file.Path artifactDirectory = artifactDirectory(
                    source.orElseThrow().toPath(), null, sourceDetails.modName());
            java.nio.file.Files.createDirectories(artifactDirectory);
            destination = artifactDirectory.resolve(
                    artifactBaseName(sourceDetails.modName()) + " project.ssmt.json");
        } catch (java.io.IOException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Load mod metadata", exception);
            showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        Optional<String> patchId = textInput(
                stage,
                GuiText.get("input.patchId.title"),
                GuiText.get("input.patchId.prompt"),
                sourceDetails.modId() + ".translation");
        if (patchId.isEmpty()) {
            return;
        }
        Optional<String> patchName = textInput(
                stage,
                GuiText.get("input.patchName.title"),
                GuiText.get("input.patchName.prompt"),
                sourceDetails.modName() + " " + GuiText.get("default.translatedName"));
        if (patchName.isEmpty()) {
            return;
        }
        Optional<File> schema = switch (schemaKind) {
            case JSON -> chooseSchema(stage);
            case CSV -> chooseCsvSchema(stage);
            case NONE -> Optional.empty();
        };
        if (schemaKind != SchemaKind.NONE && schema.isEmpty()) {
            return;
        }
        runBackgroundProjectAction(
                stage,
                GuiText.get("dialog.extracting"),
                GuiText.get("status.projectCreated"),
                cancellation -> {
            switch (schemaKind) {
                case JSON -> workspace.createWithJsonSchema(
                        source.orElseThrow().toPath(),
                        destination,
                        patchId.orElseThrow(),
                        patchName.orElseThrow(),
                        schema.orElseThrow().toPath(),
                        cancellation);
                case CSV -> workspace.createWithCsvSchema(
                        source.orElseThrow().toPath(),
                        destination,
                        patchId.orElseThrow(),
                        patchName.orElseThrow(),
                        schema.orElseThrow().toPath(),
                        cancellation);
                case NONE -> workspace.create(
                        source.orElseThrow().toPath(),
                        destination,
                        patchId.orElseThrow(),
                        patchName.orElseThrow(),
                        cancellation);
            }
        }, () -> {
            refreshEditor(table);
            updateStatus(status);
            onCompleted.run();
        });
    }

    private void exportAiPackage(Stage stage) {
        if (workspace.sourceRoot().isEmpty()) {
            showError(GuiText.get("error.aiExport"), GuiText.get("error.noProject"));
            return;
        }
        showAiExchangeGuidance(stage);
        String detectedLanguage;
        try {
            detectedLanguage = workspace.detectSourceLanguage();
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Export for online AI", exception);
            showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        Optional<String> sourceLanguage = textInput(
                stage,
                GuiText.get("dialog.sourceLanguage"),
                GuiText.get("dialog.detectedLanguage.prompt"),
                detectedLanguage);
        Optional<String> targetLanguage = textInput(
                stage,
                GuiText.get("dialog.targetLanguage"),
                GuiText.get("dialog.englishTarget.prompt"),
                GuiText.get("default.aiTargetLanguage"));
        Optional<String> batch = textInput(
                stage,
                GuiText.get("dialog.aiExportBatch.title"),
                GuiText.get("dialog.aiExportBatch.message"),
                "250");
        if (sourceLanguage.isEmpty() || targetLanguage.isEmpty() || batch.isEmpty()) {
            return;
        }
        runProjectAction("Export for online AI", GuiText.get("status.aiExported"), () -> {
            ProjectSourceDetails details = workspace.inspectSource(
                    workspace.sourceRoot().orElseThrow());
            java.nio.file.Path destination = artifactDirectory(
                    workspace.sourceRoot().orElseThrow(),
                    workspace.projectFile().orElse(null),
                    details.modName()).resolve(
                            artifactBaseName(details.modName()) + " words.json");
            java.util.List<java.nio.file.Path> parts = workspace.exportAiPackage(
                    destination,
                    details.modName(),
                    sourceLanguage.orElseThrow(),
                    targetLanguage.orElseThrow(),
                    Integer.parseInt(batch.orElseThrow().trim()));
            if (parts.size() > 1) {
                log("Split across " + parts.size() + " file(s): "
                        + parts.stream()
                                .map(part -> String.valueOf(part.getFileName()))
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
        });
    }

    private void translateProject(
            Stage stage, TableView<TranslationRow> table, Label status) {
        if (workspace.projectFile().isEmpty()) {
            showError(GuiText.get("error.translateProject"), GuiText.get("error.noProject"));
            return;
        }
        Optional<File> memory = activeTranslationMemory == null
                ? chooseTranslationMemory(stage)
                : Optional.of(activeTranslationMemory.toFile());
        if (memory.isEmpty()) {
            return;
        }
        activeTranslationMemory = memory.orElseThrow().toPath().toAbsolutePath().normalize();
        Optional<String> modeValue = textInput(stage, GuiText.get("dialog.translationMode.title"),
                GuiText.get("dialog.translationMode.message"), "SMART_DEFAULT");
        Optional<String> preferredValue = textInput(stage,
                GuiText.get("dialog.preferredLocal.title"),
                GuiText.get("dialog.preferredLocal.message"), "ARGOS");
        Optional<String> batchValue = textInput(stage, GuiText.get("dialog.projectBatch.title"),
                GuiText.get("dialog.projectBatch.message"), "32");
        if (modeValue.isEmpty() || preferredValue.isEmpty() || batchValue.isEmpty()) {
            return;
        }
        try {
            TranslationMode mode = TranslationMode.valueOf(modeValue.get().trim().toUpperCase(
                    java.util.Locale.ROOT));
            PreferredLocalProvider preferred = PreferredLocalProvider.valueOf(
                    preferredValue.get().trim().toUpperCase(java.util.Locale.ROOT));
            Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
            String configuredType = preferences.get(PROVIDER_TYPE, "");
            boolean remote = !configuredType.isBlank()
                    && AiProviderType.valueOf(configuredType) != AiProviderType.OLLAMA;
            boolean consent = false;
            if (remote && mode.allowsAi()) {
                Alert disclosure = new Alert(Alert.AlertType.CONFIRMATION,
                        GuiText.get("dialog.remoteAiConsent.message"),
                        ButtonType.YES, ButtonType.NO);
                disclosure.initOwner(stage);
                disclosure.setHeaderText(GuiText.get("dialog.remoteAiConsent.title"));
                consent = disclosure.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
                if (!consent) {
                    return;
                }
            }
            ProjectTranslationSettings settings = new ProjectTranslationSettings(
                    workspace.detectSourceLanguage(), "en", mode, preferred, 1,
                    Integer.parseInt(batchValue.get()), OptionalLong.empty(), "", "", true,
                    consent);
            boolean remoteConsent = consent;
            com.ssmt.project.ProjectTranslationResult[] completed =
                    new com.ssmt.project.ProjectTranslationResult[1];
            runBackgroundProjectAction(stage, GuiText.get("dialog.translateProject.title"),
                    GuiText.get("status.projectTranslated"), cancellation -> {
                        try (SqliteTranslationMemory tm = SqliteTranslationMemory.open(
                                activeTranslationMemory)) {
                            var argos = new ArgosTranslatePlugin(
                                    Path.of("argos-translate"), ArgosDevice.CPU,
                                    new TranslationResourceLimits(
                                            1, settings.maximumBatchSize(), OptionalLong.empty()));
                            var locally = new TranslateLocallyPlugin(
                                    Path.of("translateLocally"),
                                    TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL);
                            OfflineTranslationChain offline = new OfflineTranslationChain(
                                    new TranslationMemoryApprovedGlossary(tm), argos, locally,
                                    preferred == PreferredLocalProvider.TRANSLATE_LOCALLY);
                            Optional<com.ssmt.ai.AiTranslationProvider> provider =
                                    configuredProvider(preferences);
                            AiProviderLocation location = remote
                                    ? AiProviderLocation.REMOTE : AiProviderLocation.LOCAL;
                            completed[0] = workspace.translateProject(settings,
                                    new ChainedProjectTranslationEngine(
                                            offline, settings, provider, location),
                                    cancellation);
                        } catch (com.ssmt.tm.TranslationMemoryException exception) {
                            throw new ProjectException(
                                    "Could not open translation memory", exception);
                        }
                    }, () -> {
                        refreshEditor(table);
                        updateStatus(status);
                        if (completed[0] != null) {
                            log(GuiText.get("status.translationBackends")
                                    .replace("{0}", String.join(", ",
                                            completed[0].backendsUsed()))
                                    .replace("{1}", Integer.toString(
                                            completed[0].unresolvedEntries())));
                        }
                        if (remoteConsent) {
                            log(GuiText.get("status.remoteAiUsed"));
                        }
                    });
        } catch (IllegalArgumentException | ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Translate project", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private static Optional<com.ssmt.ai.AiTranslationProvider> configuredProvider(
            Preferences preferences) {
        String type = preferences.get(PROVIDER_TYPE, "");
        if (type.isBlank()) {
            return Optional.empty();
        }
        String credential = preferences.get(PROVIDER_CREDENTIAL, "");
        AiProviderSettings settings = new AiProviderSettings(
                AiProviderType.valueOf(type),
                URI.create(preferences.get(PROVIDER_ENDPOINT, "")),
                preferences.get(PROVIDER_MODEL, ""),
                credential.isBlank() ? Optional.empty() : Optional.of(credential));
        return Optional.of(settings.create(System::getenv));
    }

    private void exportBrowserReview(Stage stage) {
        if (workspace.sourceRoot().isEmpty()) {
            showError(GuiText.get("error.aiExport"), GuiText.get("error.noProject"));
            return;
        }
        Alert privacy = new Alert(Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.browserAiPrivacy.message"),
                ButtonType.OK, ButtonType.CANCEL);
        privacy.initOwner(stage);
        privacy.setHeaderText(GuiText.get("dialog.browserAiPrivacy.title"));
        if (privacy.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            ProjectSourceDetails details = workspace.inspectSource(
                    workspace.sourceRoot().orElseThrow());
            String detected = workspace.detectSourceLanguage();
            Optional<String> source = textInput(stage, GuiText.get("dialog.sourceLanguage"),
                    GuiText.get("dialog.detectedLanguage.prompt"), detected);
            Optional<String> target = textInput(stage, GuiText.get("dialog.targetLanguage"),
                    GuiText.get("dialog.englishTarget.prompt"), "en");
            Optional<String> batch = textInput(stage, GuiText.get("dialog.browserAiBatch.title"),
                    GuiText.get("dialog.browserAiBatch.message"), "250");
            Optional<String> terms = textInput(stage, GuiText.get("dialog.browserAiTerms.title"),
                    GuiText.get("dialog.browserAiTerms.message"), "");
            if (source.isEmpty() || target.isEmpty() || batch.isEmpty() || terms.isEmpty()) {
                return;
            }
            Path destination = artifactDirectory(workspace.sourceRoot().orElseThrow(),
                    workspace.projectFile().orElse(null), details.modName())
                    .resolve(artifactBaseName(details.modName()) + " browser AI review");
            workspace.exportBrowserReview(destination, details.modName(), source.get(),
                    target.get(), Integer.parseInt(batch.get()), terms.get());
            Preferences.userNodeForPackage(SsmtApplication.class)
                    .put(BROWSER_AI_EXPORT, destination.toAbsolutePath().normalize().toString());
            log(GuiText.get("status.browserAiExported"));
        } catch (ProjectException | IllegalArgumentException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Export browser AI review", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private void openConfiguredAiWebsite(Stage stage) {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        Optional<String> name = textInput(stage, GuiText.get("dialog.browserAiProvider.title"),
                GuiText.get("dialog.browserAiProvider.message"),
                preferences.get(BROWSER_AI_NAME, "ChatGPT"));
        if (name.isEmpty()) {
            return;
        }
        String preset = switch (name.get().trim().toLowerCase(java.util.Locale.ROOT)) {
            case "claude" -> "https://claude.ai/";
            case "gemini" -> "https://gemini.google.com/";
            default -> preferences.get(BROWSER_AI_URL, "https://chatgpt.com/");
        };
        Optional<String> url = textInput(stage, GuiText.get("dialog.browserAiUrl.title"),
                GuiText.get("dialog.browserAiUrl.message"), preset);
        if (url.isEmpty()) {
            return;
        }
        try {
            URI uri = URI.create(url.get().trim());
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Only HTTP or HTTPS AI website URLs are allowed");
            }
            preferences.put(BROWSER_AI_NAME, name.get().trim());
            preferences.put(BROWSER_AI_URL, uri.toString());
            getHostServices().showDocument(uri.toString());
        } catch (IllegalArgumentException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Open AI review website", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private void copyBrowserPrompt() {
        lastBrowserExport().ifPresentOrElse(folder -> {
            try {
                ClipboardContent content = new ClipboardContent();
                content.putString(Files.readString(folder.resolve("PROMPT.txt")));
                Clipboard.getSystemClipboard().setContent(content);
                log(GuiText.get("status.browserAiPromptCopied"));
            } catch (IOException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Copy browser AI prompt", exception);
                showError(diagnostic.summary(), diagnostic.detail());
            }
        }, () -> showError(GuiText.get("error.browserAi"),
                GuiText.get("error.browserAiNoExport")));
    }

    private void openLastBrowserExport() {
        lastBrowserExport().ifPresentOrElse(this::openFolder,
                () -> showError(GuiText.get("error.browserAi"),
                        GuiText.get("error.browserAiNoExport")));
    }

    private void reopenLastBrowserExport() {
        openLastBrowserExport();
    }

    private Optional<Path> lastBrowserExport() {
        String value = Preferences.userNodeForPackage(SsmtApplication.class)
                .get(BROWSER_AI_EXPORT, "");
        return value.isBlank() ? Optional.empty() : Optional.of(Path.of(value));
    }

    private void openFolder(Path folder) {
        try {
            if (!Files.isDirectory(folder) || !Desktop.isDesktopSupported()) {
                throw new IOException("Export folder is unavailable: " + folder);
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Open folder", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private void importBrowserReview(
            Stage stage, TableView<TranslationRow> table, Label status) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.browserAiImport"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.json"), "*.json"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        runProjectAction("Import browser AI review", GuiText.get("status.browserAiImported"), () -> {
            workspace.importBrowserReview(selected.toPath());
            refreshEditor(table);
            updateStatus(status);
        });
    }

    private void showAiExchangeGuidance(Stage stage) {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        if (preferences.getBoolean(AI_GUIDANCE_PREFERENCE, false)) {
            return;
        }
        Alert guidance = new Alert(
                Alert.AlertType.INFORMATION,
                GuiText.get("dialog.aiGuidance.message"),
                ButtonType.OK);
        guidance.initOwner(stage);
        guidance.setHeaderText(GuiText.get("dialog.aiGuidance.title"));
        guidance.showAndWait();
        preferences.putBoolean(AI_GUIDANCE_PREFERENCE, true);
    }

    private void importAiResponse(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        ProjectSourceDetails details;
        try {
            details = workspace.inspectSource(workspace.sourceRoot().orElseThrow());
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Import AI response", exception);
            showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        java.nio.file.Path response = artifactDirectory(
                workspace.sourceRoot().orElseThrow(),
                workspace.projectFile().orElse(null),
                details.modName()).resolve(
                        artifactBaseName(details.modName())
                                + " words translated.json");
        if (!java.nio.file.Files.isRegularFile(response)) {
            FileChooser chooser = fileChooser();
            chooser.setTitle(GuiText.get("chooser.aiImport"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            GuiText.get("filter.json"), "*.json"));
            chooser.setInitialFileName(java.util.Objects.requireNonNull(
                    response.getFileName(), "AI response filename").toString());
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) {
                return;
            }
            response = selected.toPath();
        }
        java.nio.file.Path responsePath = response;
        Alert memoryChoice = new Alert(
                Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.aiMemory.message"),
                ButtonType.YES,
                ButtonType.NO,
                ButtonType.CANCEL);
        memoryChoice.initOwner(stage);
        memoryChoice.setHeaderText(GuiText.get("dialog.aiMemory.title"));
        ButtonType choice = memoryChoice.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == ButtonType.CANCEL) {
            return;
        }
        Optional<File> memory = choice == ButtonType.YES
                ? activeTranslationMemory == null
                        ? chooseTranslationMemory(stage)
                        : Optional.of(activeTranslationMemory.toFile())
                : Optional.empty();
        if (choice == ButtonType.YES && memory.isEmpty()) {
            return;
        }
        memory.map(File::toPath).ifPresent(path ->
                activeTranslationMemory = path.toAbsolutePath().normalize());
        Alert approvalChoice = new Alert(
                Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.aiApproval.message"),
                ButtonType.YES,
                ButtonType.NO,
                ButtonType.CANCEL);
        approvalChoice.initOwner(stage);
        approvalChoice.setHeaderText(GuiText.get("dialog.aiApproval.title"));
        ButtonType approval = approvalChoice.showAndWait().orElse(ButtonType.CANCEL);
        if (approval == ButtonType.CANCEL) {
            return;
        }
        AiImportPolicy policy = approval == ButtonType.YES
                ? AiImportPolicy.APPROVE_ALL_VALIDATED
                : AiImportPolicy.REVIEW_DRAFTS;
        runProjectAction("Import AI response", GuiText.get("status.aiImported"), () -> {
            AiTranslationImportResult result = workspace.importAiResponse(
                    responsePath,
                    memory.map(File::toPath).orElse(null),
                    policy);
            refreshEditor(table);
            updateStatus(status);
            log(GuiText.get("status.aiImportCount")
                    .replace("{0}", Integer.toString(result.importedEntries())));
        });
    }

    private Optional<File> chooseTranslationMemory(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.tm"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        GuiText.get("filter.sqlite"), "*.db", "*.sqlite"));
        return Optional.ofNullable(chooser.showSaveDialog(stage));
    }

    private void openTranslationMemory(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.tm"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        GuiText.get("filter.sqlite"), "*.db", "*.sqlite"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            workspace.verifyTranslationMemory(selected.toPath());
            activeTranslationMemory =
                    selected.toPath().toAbsolutePath().normalize();
            rememberTranslationMemory(activeTranslationMemory);
            log(GuiText.get("status.tmOpened")
                    .replace("{0}", activeTranslationMemory.toString()));
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Open translation memory", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private void initializeTranslationMemory() {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        String remembered = preferences.get(MEMORY_PREFERENCE, "");
        String configured = System.getProperty("ssmt.catalog", "").strip();
        if (configured.isEmpty()) {
            configured = System.getenv().getOrDefault(
                    "SSMT_TRANSLATION_MEMORY", "").strip();
        }
        Path preferred = remembered.isBlank()
                ? DefaultTranslationMemoryLocator.sharedHeadlessDefault(
                        configured.isBlank() ? Optional.empty() : Optional.of(configured),
                        Optional.ofNullable(System.getenv("LOCALAPPDATA")),
                        Path.of(System.getProperty("user.home")))
                : Path.of(remembered).toAbsolutePath().normalize();
        try {
            Path parent = preferred.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            openOrCreateTranslationMemory(preferred);
        } catch (java.io.IOException | ProjectException firstFailure) {
            Path fallback = DefaultTranslationMemoryLocator.resolve(
                    Optional.empty(),
                    Optional.ofNullable(System.getProperty("jpackage.app-path")),
                    Path.of(System.getProperty("user.dir", ".")));
            try {
                Path parent = fallback.getParent();
                if (parent != null) {
                    java.nio.file.Files.createDirectories(parent);
                }
                openOrCreateTranslationMemory(fallback);
                log(GuiText.get("status.tmFallback")
                        .replace("{0}", fallback.toString()));
            } catch (java.io.IOException | ProjectException secondFailure) {
                activeTranslationMemory = null;
                log(GuiText.get("error.tmUnavailable") + ": "
                        + secondFailure.getMessage());
            }
        }
    }

    private void openOrCreateTranslationMemory(Path path) throws ProjectException {
        workspace.openOrCreateTranslationMemory(path);
        activeTranslationMemory = path.toAbsolutePath().normalize();
        rememberTranslationMemory(activeTranslationMemory);
        log(GuiText.get("status.tmOpened")
                .replace("{0}", activeTranslationMemory.toString()));
    }

    private static void rememberTranslationMemory(Path path) {
        Preferences.userNodeForPackage(SsmtApplication.class)
                .put(MEMORY_PREFERENCE, path.toString());
    }

    private void compareAndMergeTranslationMemory(Stage stage) {
        if (activeTranslationMemory == null) {
            openTranslationMemory(stage);
        }
        if (activeTranslationMemory == null) {
            return;
        }
        Alert direction = new Alert(
                Alert.AlertType.INFORMATION,
                GuiText.get("dialog.tmMerge.direction")
                        .replace("{destination}", activeTranslationMemory.toString()),
                ButtonType.OK,
                ButtonType.CANCEL);
        direction.initOwner(stage);
        direction.setHeaderText(GuiText.get("dialog.tmMerge.selectHeader"));
        if (direction.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.tmMergeSource"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        GuiText.get("filter.sqlite"), "*.db", "*.sqlite"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        Path source = selected.toPath().toAbsolutePath().normalize();
        if (source.equals(activeTranslationMemory)) {
            showError(
                    GuiText.get("error.tmMerge"),
                    GuiText.get("error.tmMergeSame")
                            .replace("{path}", source.toString()));
            return;
        }
        try {
            TranslationMemoryMergeResult comparison =
                    workspace.compareTranslationMemories(source, activeTranslationMemory);
            String summary = memoryMergeSummary(comparison);
            Alert confirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    summary,
                    ButtonType.YES,
                    ButtonType.NO);
            confirmation.initOwner(stage);
            confirmation.setHeaderText(GuiText.get("dialog.tmMerge.header"));
            if (comparison.changes() == 0
                    || confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                log(GuiText.get("status.tmCompared") + ": " + summary);
                return;
            }
            TranslationMemoryMergeResult merged =
                    workspace.mergeTranslationMemories(source, activeTranslationMemory);
            log(GuiText.get("status.tmMerged") + ": " + memoryMergeSummary(merged));
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic =
                    UserDiagnostic.failed("Compare/merge translation memory", exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private static String memoryMergeSummary(TranslationMemoryMergeResult result) {
        return GuiText.get("dialog.tmMerge.summary")
                .replace("{source}", Integer.toString(result.sourceEntries()))
                .replace("{added}", Integer.toString(result.added()))
                .replace("{upgraded}", Integer.toString(result.upgraded()))
                .replace("{identical}", Integer.toString(result.identical()))
                .replace("{conflicts}", Integer.toString(result.conflicts()));
    }

    private void openProject(
            Stage stage, TableView<TranslationRow> table, Label status, Runnable onCompleted) {
        Optional<File> source = chooseDirectory(stage, GuiText.get("chooser.source"));
        if (source.isEmpty()) {
            return;
        }
        File project =
                projectChooser(GuiText.get("chooser.projectOpen")).showOpenDialog(stage);
        if (project == null) {
            return;
        }
        runProjectAction("Open project", GuiText.get("status.projectOpened"), () -> {
            workspace.open(source.orElseThrow().toPath(), project.toPath());
            refreshEditor(table);
            updateStatus(status);
            onCompleted.run();
        });
    }

    private void buildProject(Stage stage, Label status) {
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
            details = workspace.inspectSource(workspace.sourceRoot().orElseThrow());
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Build clone", exception);
            showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        java.nio.file.Path output = artifactDirectory(
                workspace.sourceRoot().orElseThrow(),
                workspace.projectFile().orElse(null),
                details.modName()).resolve(
                        artifactBaseName(details.modName()) + " translated");
        Task<ProjectBuildResult> task = new Task<>() {
            @Override
            protected ProjectBuildResult call() throws ProjectException {
                return workspace.build(output, this::isCancelled);
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
            updateStatus(status);
            log(result.changed()
                    ? GuiText.get("status.patchPublished")
                    : GuiText.get("status.patchUnchanged"));
        });
        task.setOnCancelled(ignored -> {
            progressDialog.close();
            log(GuiText.get("status.patchCancelled"));
        });
        task.setOnFailed(ignored -> {
            progressDialog.close();
            Throwable failure = task.getException();
            UserDiagnostic diagnostic = UserDiagnostic.failed("Build clone", failure);
            showError(diagnostic.summary(), diagnostic.detail());
        });
        Thread.ofVirtual().name("ssmt-patch-build").start(task);
        progressDialog.show();
    }

    private void refreshProject(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        runRefreshTask(stage, table, status, workspace::previewRefresh);
    }

    private void previewBuild(Stage stage) {
        runProjectAction("Preview personal copy", "", () -> {
            ProjectBuildPreview preview = workspace.previewBuild();
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

    private void refreshProjectWithMemory(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        if (activeTranslationMemory == null) {
            openTranslationMemory(stage);
        }
        if (activeTranslationMemory == null) {
            return;
        }
        Optional<String> sourceLanguage = textInput(
                stage,
                GuiText.get("dialog.sourceLanguage"),
                GuiText.get("dialog.sourceLanguage.prompt"),
                GuiText.get("default.sourceLanguage"));
        Optional<String> targetLanguage = textInput(
                stage,
                GuiText.get("dialog.targetLanguage"),
                GuiText.get("dialog.targetLanguage.prompt"),
                GuiText.get("default.targetLanguage"));
        if (sourceLanguage.isEmpty() || targetLanguage.isEmpty()) {
            return;
        }
        runRefreshTask(
                stage,
                table,
                status,
                cancellation -> workspace.previewRefreshWithTranslationMemory(
                        activeTranslationMemory,
                        sourceLanguage.orElseThrow(),
                        targetLanguage.orElseThrow(),
                        0.8,
                        cancellation));
    }

    private void runRefreshTask(
            Stage stage,
            TableView<TranslationRow> table,
            Label status,
            RefreshAction action) {
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
                showRefreshResult(task.getValue(), table, status);
            } catch (ProjectException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Refresh project", exception);
                showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        task.setOnCancelled(ignored -> {
            dialog.close();
            log(GuiText.get("status.refreshCancelled"));
        });
        task.setOnFailed(ignored -> {
            dialog.close();
            Throwable failure = task.getException();
            UserDiagnostic diagnostic = UserDiagnostic.failed("Refresh project", failure);
            showError(diagnostic.summary(), diagnostic.detail());
        });
        Thread.ofVirtual().name("ssmt-project-refresh").start(task);
        dialog.show();
    }

    private void showRefreshResult(
            ProjectRefreshResult result,
            TableView<TranslationRow> table,
            Label status) throws ProjectException {
        result.report().entries().stream()
                .filter(entry -> !entry.suggestions().isEmpty())
                .forEach(entry -> editor.setSuggestions(
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
            workspace.applyRefresh(result);
            refreshEditor(table);
            updateStatus(status);
            log(GuiText.get("status.refreshApplied"));
        } else {
            log(GuiText.get("status.refreshDryRun"));
        }
    }

    private void runProjectAction(String operation, String successMessage, ProjectAction action) {
        try {
            action.run();
            log(successMessage);
        } catch (ProjectException | IllegalArgumentException exception) {
            logModel.append(new LogEntry(Instant.now(), LogLevel.ERROR, exception.getMessage()));
            refreshLogs();
            UserDiagnostic diagnostic = UserDiagnostic.failed(operation, exception);
            showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    private void runBackgroundProjectAction(
            Stage stage,
            String header,
            String successMessage,
            BackgroundProjectAction action,
            Runnable onSuccess) {
        java.util.concurrent.atomic.AtomicBoolean paused =
                new java.util.concurrent.atomic.AtomicBoolean();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws ProjectException {
                action.run(() -> {
                    while (paused.get() && !isCancelled()) {
                        java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
                    }
                    return isCancelled();
                });
                return null;
            }
        };
        ProgressIndicator progress = new ProgressIndicator();
        progress.setAccessibleText(header);
        ButtonType pause = new ButtonType(
                GuiText.get("button.pause"),
                javafx.scene.control.ButtonBar.ButtonData.LEFT);
        Alert dialog = new Alert(
                Alert.AlertType.INFORMATION, "", pause, ButtonType.CANCEL);
        dialog.initOwner(stage);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(progress);
        dialog.setOnCloseRequest(ignored -> task.cancel());
        task.setOnSucceeded(ignored -> {
            dialog.close();
            onSuccess.run();
            log(successMessage);
        });
        task.setOnCancelled(ignored -> {
            dialog.close();
            log(header + " cancelled");
        });
        task.setOnFailed(ignored -> {
            dialog.close();
            Throwable failure = task.getException();
            UserDiagnostic diagnostic = UserDiagnostic.failed(header, failure);
            showError(diagnostic.summary(), diagnostic.detail());
        });
        Thread.ofVirtual().name("ssmt-project-operation").start(task);
        javafx.scene.control.Button pauseButton =
                (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(pause);
        pauseButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            boolean nowPaused = !paused.get();
            paused.set(nowPaused);
            pauseButton.setText(GuiText.get(nowPaused ? "button.resume" : "button.pause"));
            event.consume();
        });
        dialog.show();
    }

    private void updateStatus(Label status) {
        status.setText(workspace.projectFile()
                .map(path -> {
                    java.nio.file.Path fileName = path.getFileName();
                    return fileName == null ? path.toString() : fileName.toString();
                })
                .orElse(GuiText.get("status.noProject")));
        updateCoverage();
    }

    private void updateCoverage() {
        if (coverageLabel == null) {
            return;
        }
        if (workspace.projectFile().isEmpty()) {
            coverageLabel.setText("");
            return;
        }
        try {
            var report = workspace.translationCoverage();
            coverageLabel.setText(GuiText.get("status.coverage")
                    .replace("{0}", Integer.toString(report.translatedEntries()))
                    .replace("{1}", Integer.toString(report.totalEntries()))
                    .replace("{2}", Long.toString(Math.round(report.translatedFraction() * 100))));
        } catch (ProjectException exception) {
            coverageLabel.setText("");
        }
    }

    private void refreshEditor(TableView<TranslationRow> table) {
        table.setItems(FXCollections.observableArrayList(editor.rows()));
        updateCoverage();
    }

    private void autosave() {
        workspace.projectFile().ifPresent(projectPath -> {
            try {
                workspace.autosave(ProjectWorkspaceController.recoveryRootFor(projectPath));
            } catch (ProjectException exception) {
                logModel.append(new LogEntry(
                        Instant.now(), LogLevel.WARN, exception.getMessage()));
                refreshLogs();
            }
        });
    }

    private Optional<File> chooseDirectory(Stage stage, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        projectDirectory().ifPresent(chooser::setInitialDirectory);
        return Optional.ofNullable(chooser.showDialog(stage));
    }

    private FileChooser projectChooser(String title) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                GuiText.get("filter.project"),
                "*.ssmt.json",
                "*.json"));
        return chooser;
    }

    private Optional<File> chooseSchema(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.schemaOpen"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.schema"), "*.json"));
        return Optional.ofNullable(chooser.showOpenDialog(stage));
    }

    private Optional<File> chooseCsvSchema(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.csvSchemaOpen"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.schema"), "*.json"));
        return Optional.ofNullable(chooser.showOpenDialog(stage));
    }

    private FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        projectDirectory().ifPresent(chooser::setInitialDirectory);
        return chooser;
    }

    private Optional<File> projectDirectory() {
        return workspace.projectFile()
                .map(java.nio.file.Path::getParent)
                .filter(java.nio.file.Files::isDirectory)
                .map(java.nio.file.Path::toFile);
    }

    static java.nio.file.Path artifactDirectory(
            java.nio.file.Path source,
            java.nio.file.Path project,
            String modName) {
        java.nio.file.Path normalizedSource = source.toAbsolutePath().normalize();
        if (project != null) {
            java.nio.file.Path projectParent =
                    project.toAbsolutePath().normalize().getParent();
            if (projectParent != null && !projectParent.startsWith(normalizedSource)) {
                return projectParent;
            }
        }
        java.nio.file.Path sourceParent = java.util.Objects.requireNonNull(
                normalizedSource.getParent(), "source mod parent");
        return sourceParent.resolve("Project Go - " + artifactBaseName(modName));
    }

    static String artifactBaseName(String modName) {
        String safe = java.util.Objects.requireNonNullElse(modName, "mod")
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "-")
                .strip()
                .replaceAll("[. ]+$", "");
        return safe.isBlank() ? "mod" : safe;
    }

    private static Optional<String> textInput(
            Stage stage,
            String title,
            String prompt,
            String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.initOwner(stage);
        dialog.setTitle(title);
        dialog.setHeaderText(prompt);
        return dialog.showAndWait().filter(value -> !value.isBlank());
    }

    private void log(String message) {
        logModel.append(new LogEntry(Instant.now(), LogLevel.INFO, message));
        refreshLogs();
    }

    private Tab schemaEditorTab(Stage stage) {
        JsonSchemaEditorViewModel model = new JsonSchemaEditorViewModel();
        TextField path = new TextField(GuiText.get("default.schemaPath"));
        path.setPromptText(GuiText.get("placeholder.schemaPath"));
        path.setAccessibleText(GuiText.get("accessible.schemaPath"));
        TextField pointer = new TextField(GuiText.get("default.schemaPointer"));
        pointer.setPromptText(GuiText.get("placeholder.pointer"));
        pointer.setAccessibleText(GuiText.get("accessible.schemaPointer"));
        TextArea summary = preview(GuiText.get("placeholder.schema"));
        Button add = new Button(GuiText.get("button.addPointer"));
        add.setAccessibleText(GuiText.get("accessible.addPointer"));
        add.setOnAction(ignored -> {
            try {
                model.add(java.nio.file.Path.of(path.getText()), pointer.getText());
                summary.setText(model.schema().files().stream()
                        .flatMap(file -> file.pointers().stream()
                                .map(value -> file.path() + "  " + value))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse(""));
            } catch (IllegalArgumentException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Add schema pointer", exception);
                showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        Button save = new Button(GuiText.get("button.saveSchema"));
        save.setAccessibleText(GuiText.get("accessible.saveSchema"));
        save.setOnAction(ignored -> {
            FileChooser chooser = fileChooser();
            chooser.setTitle(GuiText.get("chooser.schemaSave"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            GuiText.get("filter.schema"), "*.json"));
            File destination = chooser.showSaveDialog(stage);
            if (destination != null) {
                try {
                    model.save(destination.toPath());
                    log(GuiText.get("status.schemaSaved"));
                } catch (com.ssmt.core.exception.SsmtParseException exception) {
                    UserDiagnostic diagnostic = UserDiagnostic.failed("Save schema", exception);
                    showError(diagnostic.summary(), diagnostic.detail());
                }
            }
        });
        HBox controls = new HBox(8, path, pointer, add, save);
        return fixedTab(GuiText.get("tab.schema"), new VBox(8, controls, summary));
    }

    private Tab providerSettingsTab() {
        AiProviderSettingsViewModel model = new AiProviderSettingsViewModel();
        Preferences saved = Preferences.userNodeForPackage(SsmtApplication.class);
        ComboBox<AiProviderType> type = new ComboBox<>();
        type.getItems().addAll(AiProviderType.values());
        type.getSelectionModel().select(AiProviderType.valueOf(
                saved.get(PROVIDER_TYPE, AiProviderType.OLLAMA.name())));
        TextField endpoint = new TextField(saved.get(
                PROVIDER_ENDPOINT, GuiText.get("default.ollamaEndpoint")));
        TextField providerModel = new TextField(saved.get(
                PROVIDER_MODEL, GuiText.get("default.ollamaModel")));
        TextField credentialVariable = new TextField(saved.get(PROVIDER_CREDENTIAL, ""));
        credentialVariable.setPromptText(GuiText.get("placeholder.credential"));
        credentialVariable.setAccessibleText(GuiText.get("accessible.credential"));
        Button validate = new Button(GuiText.get("button.validateSettings"));
        Button preflight = new Button(GuiText.get("button.providerPreflight"));
        Label status = new Label(GuiText.get("status.noProvider"));
        validate.setAccessibleText(GuiText.get("accessible.validateProvider"));
        validate.setOnAction(ignored -> {
            try {
                model.update(
                        type.getValue(),
                        endpoint.getText(),
                        providerModel.getText(),
                        credentialVariable.getText());
                Preferences preferences = Preferences.userNodeForPackage(
                        SsmtApplication.class);
                preferences.put(PROVIDER_TYPE, type.getValue().name());
                preferences.put(PROVIDER_ENDPOINT, endpoint.getText().trim());
                preferences.put(PROVIDER_MODEL, providerModel.getText().trim());
                preferences.put(PROVIDER_CREDENTIAL, credentialVariable.getText().trim());
                status.setText(GuiText.get("status.providerValid"));
            } catch (IllegalArgumentException exception) {
                status.setText(exception.getMessage());
            }
        });
        preflight.setOnAction(ignored -> {
            List<String> findings = new com.ssmt.ai.LocalProviderPreflight().inspect(
                    Path.of("argos-translate"), Path.of("translateLocally"),
                    TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL);
            Alert report = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
            report.setHeaderText(GuiText.get("dialog.providerPreflight.title"));
            TextArea details = new TextArea(String.join(System.lineSeparator(), findings));
            details.setEditable(false);
            details.setWrapText(true);
            report.getDialogPane().setContent(details);
            report.showAndWait();
        });
        HBox controls = new HBox(
                8, type, endpoint, providerModel, credentialVariable, validate, preflight);
        return fixedTab(GuiText.get("tab.provider"), new VBox(8, controls, status));
    }

    private void showFirstRunGuidance(Stage stage) {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        if (preferences.getBoolean("guidanceShown", false)) {
            return;
        }
        Alert guidance = new Alert(
                Alert.AlertType.INFORMATION,
                GuiText.get("dialog.firstRun.message"),
                ButtonType.OK);
        guidance.initOwner(stage);
        guidance.setHeaderText(GuiText.get("dialog.firstRun.title"));
        guidance.showAndWait();
        preferences.putBoolean("guidanceShown", true);
    }

    private static void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
        alert.setHeaderText(header);
        TextArea details = new TextArea(message);
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(6);
        alert.getDialogPane().setContent(details);
        alert.showAndWait();
    }

    private Tab fontCoverageTab(Stage stage) {
        fontCoverageTable = new TableView<>();
        fontCoverageTable.setPlaceholder(new Label(GuiText.get("placeholder.fontCoverage")));
        fontCoverageTable.getColumns().add(column(
                GuiText.get("column.file"), row -> row.sourceFile().toString()));
        fontCoverageTable.getColumns().add(column(
                GuiText.get("column.key"), row -> row.key()));
        fontCoverageTable.getColumns().add(column(
                GuiText.get("column.missing"), row -> row.missingCharacters()));
        Button checkFont = new Button(GuiText.get("button.checkFontCoverage"));
        checkFont.setAccessibleText(GuiText.get("accessible.checkFontCoverage"));
        checkFont.setOnAction(event -> {
            FileChooser chooser = fileChooser();
            chooser.setTitle(GuiText.get("chooser.fontOpen"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(GuiText.get("filter.font"), "*.fnt"));
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) {
                return;
            }
            try {
                List<com.ssmt.project.FontCoverageFinding> findings =
                        workspace.checkFontCoverage(selected.toPath());
                fontCoverageTable.setItems(FXCollections.observableArrayList(findings));
                log(GuiText.get("status.fontCoverageChecked")
                        .replace("{0}", Integer.toString(findings.size())));
            } catch (ProjectException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Check font coverage", exception);
                showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        VBox content = new VBox(8, new HBox(8, checkFont), fontCoverageTable);
        content.setPadding(new Insets(12));
        return fixedTab(GuiText.get("tab.fontCoverage"), content);
    }

    private Tab logTab() {
        logTable = new TableView<>();
        logTable.setPlaceholder(new Label(GuiText.get("placeholder.diagnostics")));
        logTable.getColumns().add(column(
                GuiText.get("column.time"), row -> LOG_TIME.format(row.timestamp())));
        logTable.getColumns().add(column(
                GuiText.get("column.level"), row -> row.level().name()));
        logTable.getColumns().add(column(
                GuiText.get("column.message"), LogEntry::message));
        refreshLogs();
        return fixedTab(GuiText.get("tab.diagnostics"), logTable);
    }

    private void refreshLogs() {
        if (logTable != null) {
            logTable.setItems(FXCollections.observableArrayList(logModel.entries()));
        }
    }

    /**
     * Builds a {@code SplitMenuButton} dropdown entry whose accessible text
     * matches what an equivalent standalone {@code Button} would have had.
     * {@link javafx.scene.control.MenuItem} itself has no accessible-text
     * API (it is not a {@code Node}), so the label is wrapped in a
     * {@link CustomMenuItem} whose content node carries the accessible text
     * instead.
     *
     * @param label visible menu item text
     * @param accessibleText accessible text carried by the wrapped content node
     * @param action invoked when the item is chosen
     * @return a hide-on-click custom menu item
     */
    private static CustomMenuItem schemaVariantMenuItem(
            String label,
            String accessibleText,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Label content = new Label(label);
        content.setAccessibleText(accessibleText);
        CustomMenuItem item = new CustomMenuItem(content, true);
        item.setOnAction(action);
        return item;
    }

    private static TextArea preview(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setEditable(false);
        area.setWrapText(true);
        return area;
    }

    private static <T> TableColumn<T, String> column(
            String title,
            java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static MenuItem menuItem(String text, Button action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(ignored -> action.fire());
        return item;
    }

    private static Tab fixedTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    @FunctionalInterface
    private interface ProjectAction {
        void run() throws ProjectException;
    }

    @FunctionalInterface
    private interface BackgroundProjectAction {
        void run(com.ssmt.core.CancellationToken cancellation) throws ProjectException;
    }

    @FunctionalInterface
    private interface RefreshAction {
        ProjectRefreshResult run(com.ssmt.core.CancellationToken cancellation)
                throws ProjectException;
    }
}
