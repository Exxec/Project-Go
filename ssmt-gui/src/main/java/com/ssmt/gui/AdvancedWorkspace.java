package com.ssmt.gui;

import com.ssmt.ai.AiProviderType;
import com.ssmt.ai.AiProviderSettings;
import com.ssmt.project.ProjectException;
import com.ssmt.project.ProjectRefreshResult;
import com.ssmt.tm.TranslationMemoryMergeResult;
import java.io.File;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.prefs.Preferences;
import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Per-window state and shared coordination for the advanced workspace. */
final class AdvancedWorkspace {
    private final java.util.function.Supplier<javafx.application.HostServices> hostServices;
    private final WorkspaceProjectActions workspaceProjectActions = new WorkspaceProjectActions(this);
    private final WorkspaceAiActions workspaceAiActions = new WorkspaceAiActions(this);
    private final WorkspaceMemoryActions workspaceMemoryActions = new WorkspaceMemoryActions(this);

    AdvancedWorkspace(java.util.function.Supplier<javafx.application.HostServices> hostServices) {
        this.hostServices = hostServices;
    }

    javafx.application.HostServices getHostServices() {
        return hostServices.get();
    }

    boolean hasUnsavedChanges() {
        return workspace.hasUnsavedChanges();
    }


    static final DateTimeFormatter LOG_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    static final String MEMORY_PREFERENCE = "translationMemoryPath";
    static final String AI_GUIDANCE_PREFERENCE = "aiExchangeGuidanceShown";
    static final String BROWSER_AI_NAME = "browserAiProviderName";
    static final String BROWSER_AI_URL = "browserAiProviderUrl";
    static final String BROWSER_AI_EXPORT = "browserAiLastExport";
    static final String PROVIDER_TYPE = "configuredProviderType";
    static final String PROVIDER_ENDPOINT = "configuredProviderEndpoint";
    static final String PROVIDER_MODEL = "configuredProviderModel";
    static final String PROVIDER_CREDENTIAL = "configuredProviderCredentialEnv";

    final LogDashboardViewModel logModel = new LogDashboardViewModel(1_000);
    final TranslationEditorController editor = new TranslationEditorController();
    final ProjectWorkspaceController workspace =
            new ProjectWorkspaceController(editor);
    Path activeTranslationMemory;
    TableView<LogEntry> logTable;
    Label coverageLabel;
    TableView<com.ssmt.project.FontCoverageFinding> fontCoverageTable;
    TabPane navigationTabs;
    Tab editorNavigationTab;
    TableView<TranslationRow> editorTable;
    Label editorStatus;


    Tab create(Stage stage) {
        editorNavigationTab = editorTab(stage);
        navigationTabs = new TabPane(
                welcomeTab(stage),
                editorNavigationTab,
                toolsTab(stage));
        Tab advanced = fixedTab(GuiText.get("normal.advanced"), navigationTabs);
        java.util.concurrent.atomic.AtomicBoolean initialized = new java.util.concurrent.atomic.AtomicBoolean();
        advanced.setOnSelectionChanged(event -> {
            if (advanced.isSelected() && initialized.compareAndSet(false, true)) { initializeTranslationMemory(); }
        });
        return advanced;
    }

    /**
     * The intentional first screen: the three actions a new user needs before
     * encountering the editor and its optional tools.
     */
    Tab welcomeTab(Stage stage) {
        return WelcomeTab.create(this, stage);
    }

    /** Keeps infrequently used configuration and diagnostics reachable without competing with work. */
    Tab toolsTab(Stage stage) {
        return WorkspaceToolsTab.create(this, stage);
    }

    void showEditor() {
        navigationTabs.getSelectionModel().select(editorNavigationTab);
    }

    Tab editorTab(Stage stage) {
        return TranslationEditorTab.create(this, stage);
    }

    void auditGlossary(Stage stage) {
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

    void exportTranslationReport(Stage stage) {
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

    enum SchemaKind { NONE, JSON, CSV }

    void openSampleProject(
            Stage stage, TableView<TranslationRow> table, Label status, Runnable onCompleted) {
        workspaceProjectActions.openSampleProject(stage, table, status, onCompleted);
    }

    Tab projectInfoTab() {
        return ProjectInfoTab.create(this);
    }

    void addPathRow(VBox parent, String name, Optional<Path> path) {
        Label value = new Label(path.map(Path::toString)
                .orElse(GuiText.get("status.notSet")));
        value.setWrapText(true);
        Button reveal = new Button(GuiText.get("button.openFolder"));
        reveal.setDisable(path.isEmpty() || !java.nio.file.Files.exists(path.orElseThrow()));
        reveal.setOnAction(ignored -> path.ifPresent(this::revealPath));
        parent.getChildren().add(new HBox(8, new Label(name), value, reveal));
    }

    void revealPath(Path path) {
        Path target = java.nio.file.Files.isDirectory(path) ? path : path.getParent();
        if (target == null || !java.nio.file.Files.isDirectory(target)) {
            return;
        }
        getHostServices().showDocument(target.toUri().toString());
    }

    String workflowSummary(ProjectWorkspaceInfo info) {
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

    void createProject(
            Stage stage,
            TableView<TranslationRow> table,
            Label status,
            SchemaKind schemaKind,
            Runnable onCompleted) {
        workspaceProjectActions.createProject(stage, table, status, schemaKind, onCompleted);
    }

    void exportAiPackage(Stage stage) {
        workspaceAiActions.exportAiPackage(stage);
    }

    void translateProject(
            Stage stage, TableView<TranslationRow> table, Label status) {
        workspaceAiActions.translateProject(stage, table, status);
    }

    static Optional<com.ssmt.ai.AiTranslationProvider> configuredProvider(
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

    void exportBrowserReview(Stage stage) {
        workspaceAiActions.exportBrowserReview(stage);
    }

    void openConfiguredAiWebsite(Stage stage) {
        workspaceAiActions.openConfiguredAiWebsite(stage);
    }

    void copyBrowserPrompt() {
        workspaceAiActions.copyBrowserPrompt();
    }

    void openLastBrowserExport() {
        workspaceAiActions.openLastBrowserExport();
    }

    void reopenLastBrowserExport() {
        workspaceAiActions.reopenLastBrowserExport();
    }

    Optional<Path> lastBrowserExport() {
        return workspaceAiActions.lastBrowserExport();
    }

    void openFolder(Path folder) {
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

    void importBrowserReview(
            Stage stage, TableView<TranslationRow> table, Label status) {
        workspaceAiActions.importBrowserReview(stage, table, status);
    }

    void showAiExchangeGuidance(Stage stage) {
        workspaceAiActions.showAiExchangeGuidance(stage);
    }

    void importAiResponse(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        workspaceAiActions.importAiResponse(stage, table, status);
    }

    Optional<File> chooseTranslationMemory(Stage stage) {
        return workspaceMemoryActions.chooseTranslationMemory(stage);
    }

    void openTranslationMemory(Stage stage) {
        workspaceMemoryActions.openTranslationMemory(stage);
    }

    void initializeTranslationMemory() {
        workspaceMemoryActions.initializeTranslationMemory();
    }

    void openOrCreateTranslationMemory(Path path) throws ProjectException {
        workspaceMemoryActions.openOrCreateTranslationMemory(path);
    }

    static void rememberTranslationMemory(Path path) {
        Preferences.userNodeForPackage(SsmtApplication.class)
                .put(MEMORY_PREFERENCE, path.toString());
    }

    void compareAndMergeTranslationMemory(Stage stage) {
        workspaceMemoryActions.compareAndMergeTranslationMemory(stage);
    }

    static String memoryMergeSummary(TranslationMemoryMergeResult result) {
        return GuiText.get("dialog.tmMerge.summary")
                .replace("{source}", Integer.toString(result.sourceEntries()))
                .replace("{added}", Integer.toString(result.added()))
                .replace("{upgraded}", Integer.toString(result.upgraded()))
                .replace("{identical}", Integer.toString(result.identical()))
                .replace("{conflicts}", Integer.toString(result.conflicts()));
    }

    void openProject(
            Stage stage, TableView<TranslationRow> table, Label status, Runnable onCompleted) {
        workspaceProjectActions.openProject(stage, table, status, onCompleted);
    }

    void buildProject(Stage stage, Label status) {
        workspaceProjectActions.buildProject(stage, status);
    }

    void refreshProject(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        workspaceProjectActions.refreshProject(stage, table, status);
    }

    void previewBuild(Stage stage) {
        workspaceProjectActions.previewBuild(stage);
    }

    void refreshProjectWithMemory(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        workspaceProjectActions.refreshProjectWithMemory(stage, table, status);
    }

    void runRefreshTask(
            Stage stage,
            TableView<TranslationRow> table,
            Label status,
            RefreshAction action) {
        workspaceProjectActions.runRefreshTask(stage, table, status, action);
    }

    void showRefreshResult(
            ProjectRefreshResult result,
            TableView<TranslationRow> table,
            Label status) throws ProjectException {
        workspaceProjectActions.showRefreshResult(result, table, status);
    }

    void runProjectAction(String operation, String successMessage, ProjectAction action) {
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

    void runBackgroundProjectAction(
            Stage stage,
            String header,
            String successMessage,
            BackgroundProjectAction action,
            Runnable onSuccess) {
        java.util.concurrent.atomic.AtomicBoolean paused =
                new java.util.concurrent.atomic.AtomicBoolean();
        Task<Void> task = new Task<>() {
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

    void updateStatus(Label status) {
        status.setText(workspace.projectFile()
                .map(path -> {
                    java.nio.file.Path fileName = path.getFileName();
                    return fileName == null ? path.toString() : fileName.toString();
                })
                .orElse(GuiText.get("status.noProject")));
        updateCoverage();
    }

    void updateCoverage() {
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

    void refreshEditor(TableView<TranslationRow> table) {
        table.setItems(FXCollections.observableArrayList(editor.rows()));
        updateCoverage();
    }

    void autosave() {
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

    Optional<File> chooseDirectory(Stage stage, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        projectDirectory().ifPresent(chooser::setInitialDirectory);
        return Optional.ofNullable(chooser.showDialog(stage));
    }

    FileChooser projectChooser(String title) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                GuiText.get("filter.project"),
                "*.ssmt.json",
                "*.json"));
        return chooser;
    }

    Optional<File> chooseSchema(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.schemaOpen"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.schema"), "*.json"));
        return Optional.ofNullable(chooser.showOpenDialog(stage));
    }

    Optional<File> chooseCsvSchema(Stage stage) {
        FileChooser chooser = fileChooser();
        chooser.setTitle(GuiText.get("chooser.csvSchemaOpen"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.schema"), "*.json"));
        return Optional.ofNullable(chooser.showOpenDialog(stage));
    }

    FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        projectDirectory().ifPresent(chooser::setInitialDirectory);
        return chooser;
    }

    Optional<File> projectDirectory() {
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

    static Optional<String> textInput(
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

    void log(String message) {
        logModel.append(new LogEntry(Instant.now(), LogLevel.INFO, message));
        refreshLogs();
    }

    Tab schemaEditorTab(Stage stage) {
        return SchemaEditorTab.create(this, stage);
    }

    Tab providerSettingsTab() {
        return ProviderSettingsTab.create(this);
    }

    void showFirstRunGuidance(Stage stage) {
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

    static void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
        alert.setHeaderText(header);
        TextArea details = new TextArea(message);
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(6);
        alert.getDialogPane().setContent(details);
        alert.showAndWait();
    }

    Tab fontCoverageTab(Stage stage) {
        return FontCoverageTab.create(this, stage);
    }

    Tab logTab() {
        return LogDashboardTab.create(this);
    }

    void refreshLogs() {
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
    static CustomMenuItem schemaVariantMenuItem(
            String label,
            String accessibleText,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Label content = new Label(label);
        content.setAccessibleText(accessibleText);
        CustomMenuItem item = new CustomMenuItem(content, true);
        item.setOnAction(action);
        return item;
    }

    static TextArea preview(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setEditable(false);
        area.setWrapText(true);
        return area;
    }

    static <T> TableColumn<T, String> column(
            String title,
            java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    static MenuItem menuItem(String text, Button action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(ignored -> action.fire());
        return item;
    }

    static Tab fixedTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    @FunctionalInterface
    interface ProjectAction {
        void run() throws ProjectException;
    }

    @FunctionalInterface
    interface BackgroundProjectAction {
        void run(com.ssmt.core.CancellationToken cancellation) throws ProjectException;
    }

    @FunctionalInterface
    interface RefreshAction {
        ProjectRefreshResult run(com.ssmt.core.CancellationToken cancellation)
                throws ProjectException;
    }
}
