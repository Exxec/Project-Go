package com.ssmt.gui;

import com.ssmt.patcher.PatchRecoveryService;
import com.ssmt.project.StorageHygieneService;
import com.ssmt.project.LegacyProjectCandidate;
import com.ssmt.project.ProjectException;
import com.ssmt.project.SourceIdentityConflictException;
import com.ssmt.project.TranslationWorkflow;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** One drop-driven normal flow; optional pickers and maintenance stay secondary. */
final class TranslationWorkflowPane extends VBox {
    private final TranslationWorkflowController controller =
            new TranslationWorkflowController(new TranslationWorkflow());
    private final WorkflowMaintenanceController maintenance = new WorkflowMaintenanceController();
    private final Label status = new Label(GuiText.get("normal.chooseHelp"));
    private final Label prompt = new Label();
    private final Label[] stages = new Label[3];
    private final Label dropTarget = new Label(GuiText.get("normal.drop"));
    private final Button primary = new Button();
    private final Button openOutput = new Button(GuiText.get("normal.openOutput"));
    private final HBox resultActions = new HBox(10, openOutput);
    private final MenuButton otherActions = new MenuButton(GuiText.get("normal.otherActions"));
    private final MenuItem exportAction = new MenuItem(GuiText.get("normal.action.exportAgain"));
    private final MenuItem importAction = new MenuItem(GuiText.get("normal.action.importFile"));
    private final MenuItem buildAction = new MenuItem(GuiText.get("normal.action.buildNow"));
    private final TranslationWorkflowPresentation presentation = new TranslationWorkflowPresentation();
    private final Stage stage;
    private final Runnable openAdvanced;

    TranslationWorkflowPane(Stage stage) {
        this(stage, () -> { });
    }

    TranslationWorkflowPane(Stage stage, Runnable openAdvanced) {
        super(16);
        this.stage = stage;
        this.openAdvanced = openAdvanced;
        setPadding(new Insets(24));
        Label title = new Label(GuiText.get("normal.title"));
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label explanation = new Label(GuiText.get("normal.explanation"));
        explanation.setWrapText(true);
        status.setWrapText(true);
        prompt.setWrapText(true);
        stages[0] = new Label(GuiText.get("normal.stage.choose"));
        stages[1] = new Label(GuiText.get("normal.stage.translate"));
        stages[2] = new Label(GuiText.get("normal.stage.install"));

        dropTarget.setMaxWidth(Double.MAX_VALUE);
        dropTarget.setPadding(new Insets(28));
        dropTarget.setStyle("-fx-border-color: #7a8793; -fx-border-width: 2px;"
                + " -fx-border-style: segments(8, 6); -fx-background-color: #f4f6f8;"
                + " -fx-font-size: 16px; -fx-alignment: center;");
        setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && event.getDragboard().getFiles().size() == 1) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragDropped(event -> {
            boolean accepted = event.getDragboard().hasFiles()
                    && event.getDragboard().getFiles().size() == 1;
            if (accepted) {
                acceptDropped(event.getDragboard().getFiles().getFirst().toPath());
            } else {
                status.setText(GuiText.get("normal.drop.single"));
            }
            event.setDropCompleted(accepted);
            event.consume();
        });

        MenuItem chooseFolder = menuItem(GuiText.get("normal.action.chooseFolder"), this::chooseMod);
        MenuItem chooseFile = menuItem(GuiText.get("normal.action.chooseZip"), this::chooseInputFile);
        exportAction.setOnAction(event -> exportTranslation());
        importAction.setOnAction(event -> importTranslation());
        buildAction.setOnAction(event -> buildCopy());
        MenuItem settings = menuItem(GuiText.get("normal.settings"), this::showSettings);
        otherActions.getItems().addAll(chooseFolder, chooseFile, exportAction, importAction,
                buildAction, settings);
        openOutput.setOnAction(event -> openOutputFolder());
        resultActions.setManaged(false);
        resultActions.setVisible(false);
        getChildren().addAll(title, explanation, new HBox(20, stages), prompt, dropTarget,
                new HBox(10, primary, otherActions), resultActions, status);
        update();
    }

    private void chooseMod() {
        DirectoryChooser picker = new DirectoryChooser();
        picker.setTitle(GuiText.get("normal.primary.choose"));
        File folder = picker.showDialog(stage);
        if (folder != null) {
            loadInput(folder.toPath());
        }
    }

    private void chooseInputFile() {
        FileChooser picker = new FileChooser();
        picker.setTitle(GuiText.get("normal.primary.choose"));
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Mod ZIP or mod_info.json", "*.zip", "*.json"));
        File file = picker.showOpenDialog(stage);
        if (file != null) {
            loadInput(file.toPath());
        }
    }

    private void loadInput(Path input) {
        run(TranslationWorkflowPresentation.Action.CHOOSE_MOD,
                () -> controller.loadInput(input), GuiText.get("normal.loaded"), "Open mod");
    }

    private void acceptDropped(Path input) {
        setDisable(true);
        status.setText(GuiText.get("normal.working"));
        Task<TranslationWorkflowController.DropResult> task = new Task<>() {
            @Override protected TranslationWorkflowController.DropResult call() throws Exception {
                return controller.acceptDrop(input);
            }
        };
        task.setOnSucceeded(event -> {
            setDisable(false);
            TranslationWorkflowPresentation.Action completed = task.getValue()
                    == TranslationWorkflowController.DropResult.MOD_LOADED
                    ? TranslationWorkflowPresentation.Action.CHOOSE_MOD
                    : TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION;
            presentation.completed(completed, readyToBuild());
            update();
            String success = task.getValue() == TranslationWorkflowController.DropResult.MOD_LOADED
                    ? GuiText.get("normal.loaded") : GuiText.get("normal.imported");
            status.setText(success + "\n" + summary());
        });
        task.setOnFailed(event -> handleFailure("Open dropped item", task.getException()));
        Thread.ofVirtual().name("project-go-drop").start(task);
    }

    private void exportTranslation() {
        File file = jsonChooser(requestFilename()).showSaveDialog(stage);
        if (file != null) {
            run(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION,
                    () -> controller.exportTranslation(file.toPath()), GuiText.get("normal.exported"),
                    "Create translation file");
        }
    }

    private void importTranslation() {
        File file = jsonChooser(requestFilename()).showOpenDialog(stage);
        if (file != null) {
            run(TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION,
                    () -> controller.importTranslation(file.toPath()), GuiText.get("normal.imported"),
                    "Open returned translation file");
        }
    }

    /** Readable suggested filename; the returned file may keep any name at all. */
    private String requestFilename() {
        try {
            return controller.aiRequestFilename();
        } catch (ProjectException exception) {
            return "translation.json";
        }
    }

    private void buildCopy() {
        DirectoryChooser picker = new DirectoryChooser();
        picker.setTitle(GuiText.get("normal.output"));
        controller.modsDestination().filter(Files::isDirectory).map(Path::toFile)
                .ifPresent(picker::setInitialDirectory);
        File folder = picker.showDialog(stage);
        if (folder != null) {
            try {
                Path output = controller.outputBelow(folder.toPath());
                String installed = GuiText.get("normal.built")
                        .replace("{0}", controller.installedFolderName());
                run(TranslationWorkflowPresentation.Action.BUILD_COPY,
                        () -> controller.buildPatch(output), installed + "\n" + output,
                        "Install translated copy");
            } catch (Exception exception) {
                failed("Install translated copy", exception);
            }
        }
    }

    private static FileChooser jsonChooser(String name) {
        FileChooser picker = new FileChooser();
        picker.setInitialFileName(name);
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Translation JSON", "*.json"));
        return picker;
    }

    private void run(TranslationWorkflowPresentation.Action completed, Action action,
            String success, String operation) {
        setDisable(true);
        status.setText(GuiText.get("normal.working"));
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                action.run();
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            setDisable(false);
            presentation.completed(completed, readyToBuild());
            update();
            status.setText(success + "\n" + summary());
        });
        task.setOnFailed(event -> handleFailure(operation, task.getException()));
        Thread.ofVirtual().name("project-go-workflow").start(task);
    }

    private void failed(String operation, Throwable failure) {
        setDisable(false);
        update();
        UserDiagnostic diagnostic = UserDiagnostic.failed(operation, failure);
        status.setText(diagnostic.summary() + "\n" + diagnostic.detail() + "\n" + summary());
    }

    private void handleFailure(String operation, Throwable failure) {
        setDisable(false);
        if (failure instanceof LegacyProjectsFoundException legacy) {
            showLegacyChoice(legacy);
        } else if (failure instanceof SourceIdentityConflictException conflict) {
            showLineageChoice(conflict);
        } else {
            failed(operation, failure);
        }
    }

    /**
     * Asks the user to decide between two genuinely different mods that declare one
     * id. Fingerprints, digests, and workspace names are deliberately not shown.
     */
    private void showLineageChoice(SourceIdentityConflictException conflict) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(GuiText.get("normal.lineage.title"));
        ButtonType usePrevious = new ButtonType(GuiText.get("normal.lineage.usePrevious"),
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType startSeparately = new ButtonType(GuiText.get("normal.lineage.startSeparately"),
                javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(usePrevious, startSeparately, ButtonType.CANCEL);
        Label explanation = new Label(GuiText.get("normal.lineage.explanation")
                .replace("{0}", conflict.previousModName())
                .replace("{1}", Integer.toString(conflict.previousEntries()))
                .replace("{2}", conflict.currentModName())
                .replace("{3}", Integer.toString(conflict.currentEntries())));
        explanation.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(10, explanation));
        dialog.showAndWait().ifPresent(result -> {
            if (result == usePrevious) {
                run(TranslationWorkflowPresentation.Action.CHOOSE_MOD,
                        () -> controller.resolveLineage(conflict.source(),
                                TranslationWorkflow.LineageChoice.USE_PREVIOUS),
                        GuiText.get("normal.loaded"), "Use previous translation");
            } else if (result == startSeparately) {
                run(TranslationWorkflowPresentation.Action.CHOOSE_MOD,
                        () -> controller.resolveLineage(conflict.source(),
                                TranslationWorkflow.LineageChoice.START_SEPARATELY),
                        GuiText.get("normal.lineage.separated"), "Start separate translation");
            }
        });
        update();
    }

    private void showLegacyChoice(LegacyProjectsFoundException legacy) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(GuiText.get("normal.legacy.title"));
        ButtonType importOld = new ButtonType(GuiText.get("normal.legacy.import"),
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType startFresh = new ButtonType(GuiText.get("normal.legacy.fresh"),
                javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(importOld, startFresh, ButtonType.CANCEL);
        ComboBox<LegacyProjectCandidate> choices = new ComboBox<>(
                javafx.collections.FXCollections.observableArrayList(legacy.candidates()));
        choices.setMaxWidth(Double.MAX_VALUE);
        choices.setConverter(new StringConverter<>() {
            @Override public String toString(LegacyProjectCandidate candidate) {
                if (candidate == null) { return ""; }
                return candidate.projectName() + " — " + candidate.translatedEntries() + "/"
                        + candidate.entries() + " translated — " + candidate.modifiedAt()
                        + " — " + candidate.file();
            }
            @Override public LegacyProjectCandidate fromString(String value) { return null; }
        });
        if (legacy.candidates().size() == 1) { choices.getSelectionModel().selectFirst(); }
        Button importButton = (Button) dialog.getDialogPane().lookupButton(importOld);
        importButton.disableProperty().bind(choices.valueProperty().isNull());
        Label explanation = new Label(GuiText.get("normal.legacy.explanation"));
        explanation.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(10, explanation, choices));
        dialog.showAndWait().ifPresent(result -> {
            if (result == importOld) {
                run(TranslationWorkflowPresentation.Action.CHOOSE_MOD,
                        () -> controller.adoptLegacy(legacy.input(), choices.getValue()),
                        GuiText.get("normal.legacy.imported"), "Import old project");
            } else if (result == startFresh) {
                run(TranslationWorkflowPresentation.Action.CHOOSE_MOD,
                        () -> controller.startFresh(legacy.input()),
                        GuiText.get("normal.loaded"), "Start fresh translation");
            }
        });
    }

    private boolean readyToBuild() {
        return controller.session().map(session -> session.project().entries().stream()
                .filter(entry -> !entry.originalText().isBlank())
                .allMatch(entry -> !entry.translatedText().isBlank())).orElse(false);
    }

    private void update() {
        TranslationWorkflowPresentation.Action action = presentation.action();
        primary.setText(action.buttonText());
        primary.setOnAction(event -> perform(action));
        primary.setDefaultButton(true);
        prompt.setText(action.prompt());
        boolean complete = action == TranslationWorkflowPresentation.Action.COMPLETE;
        boolean missing = controller.session().isEmpty();
        exportAction.setDisable(missing);
        importAction.setDisable(missing);
        buildAction.setDisable(missing || !readyToBuild());
        resultActions.setManaged(complete);
        resultActions.setVisible(complete);
        openOutput.setDisable(controller.lastOutput().isEmpty());
        for (int i = 0; i < stages.length; i++) {
            int number = i + 1;
            stages[i].setStyle(number == action.stage()
                    ? "-fx-font-weight: bold; -fx-underline: true;"
                    : number < action.stage() ? "-fx-opacity: 0.65;" : "-fx-opacity: 0.4;");
        }
    }

    private void perform(TranslationWorkflowPresentation.Action action) {
        switch (action) {
            case CHOOSE_MOD -> chooseMod();
            case EXPORT_TRANSLATION -> exportTranslation();
            case IMPORT_TRANSLATION -> importTranslation();
            case BUILD_COPY -> buildCopy();
            case COMPLETE -> reset();
        }
    }

    private void reset() {
        controller.reset();
        presentation.reset();
        status.setText(GuiText.get("normal.chooseHelp"));
        update();
    }

    private void openOutputFolder() {
        controller.lastOutput().ifPresent(output -> {
            try {
                if (!Desktop.isDesktopSupported() || !Files.isDirectory(output)) {
                    throw new IOException("The translated mod folder is not available: " + output);
                }
                Desktop.getDesktop().open(output.toFile());
            } catch (IOException | UnsupportedOperationException exception) {
                failed("Open translated mod folder", exception);
            }
        });
    }

    private void showSettings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(GuiText.get("normal.settings.title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        String destination = controller.modsDestination().map(Path::toString)
                .orElse(GuiText.get("normal.settings.destination.none"));
        Label location = new Label(GuiText.get("normal.settings.destination")
                .replace("{0}", destination));
        TextArea cleanupStatus = new TextArea();
        cleanupStatus.setEditable(false);
        cleanupStatus.setWrapText(true);
        cleanupStatus.setPrefRowCount(8);
        Button previewCleanup = new Button(GuiText.get("normal.settings.cleanup"));
        Button clean = new Button(GuiText.get("normal.settings.clean"));
        clean.setDisable(true);
        final StorageHygieneService.Preview[] approved = new StorageHygieneService.Preview[1];
        previewCleanup.setOnAction(event -> background(previewCleanup, cleanupStatus::setText,
                () -> maintenance.previewCleanup(controller.session().map(
                        TranslationWorkflow.Session::source)), preview -> {
                    approved[0] = preview;
                    clean.setDisable(preview.items().isEmpty());
                    cleanupStatus.setText(preview.items().isEmpty()
                            ? GuiText.get("normal.settings.cleanup.empty")
                            : GuiText.get("normal.settings.cleanup.summary")
                                    .replace("{0}", Integer.toString(preview.files()))
                                    .replace("{1}", Long.toString(preview.bytes()))
                                    + "\n" + preview.items().stream()
                                            .map(StorageHygieneService.Item::relativePath)
                                            .collect(java.util.stream.Collectors.joining("\n")));
                }, "Preview storage cleanup"));
        Button closeDialog = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        Button advanced = new Button(GuiText.get("normal.settings.advanced"));
        var mutating = new java.util.concurrent.atomic.AtomicBoolean();
        dialog.setOnCloseRequest(event -> {
            if (mutating.get()) {
                event.consume();
            }
        });
        Runnable finishMutation = () -> {
            mutating.set(false);
            closeDialog.setDisable(false);
            advanced.setDisable(false);
        };
        clean.setOnAction(event -> {
            StorageHygieneService.Preview preview = approved[0];
            if (preview != null) {
                mutating.set(true);
                closeDialog.setDisable(true);
                advanced.setDisable(true);
                background(clean, cleanupStatus::setText, () -> {
                    maintenance.cleanup(preview);
                    return null;
                }, ignored -> {
                    approved[0] = null;
                    clean.setDisable(true);
                    cleanupStatus.setText(GuiText.get("normal.settings.cleanup.done"));
                }, "Clean application storage", finishMutation);
            }
        });

        Label recoveryStatus = new Label(GuiText.get("normal.settings.recovery.none"));
        recoveryStatus.setWrapText(true);
        Button restore = new Button(GuiText.get("normal.settings.restore"));
        restore.setDisable(true);
        final PatchRecoveryService.Preview[] recoverable = new PatchRecoveryService.Preview[1];
        java.util.Optional<Path> recoveryOutput = controller.recoveryOutput().or(() ->
                controller.modsDestination().flatMap(destinationRoot -> {
            try {
                return java.util.Optional.of(controller.outputBelow(destinationRoot));
            } catch (Exception exception) {
                return java.util.Optional.empty();
            }
        }));
        recoveryOutput.ifPresent(output -> background(restore, recoveryStatus::setText,
                () -> maintenance.inspectRecovery(output), preview -> {
                    recoverable[0] = preview;
                    restore.setDisable(preview.action() != PatchRecoveryService.Action.RESTORE_PREVIOUS);
                    recoveryStatus.setText(switch (preview.action()) {
                        case NONE -> GuiText.get("normal.settings.recovery.none");
                        case RESTORE_PREVIOUS -> GuiText.get("normal.settings.recovery.ready");
                        case REVIEW_REQUIRED -> GuiText.get("normal.settings.recovery.review");
                    });
                }, "Inspect interrupted install"));
        restore.setOnAction(event -> {
            PatchRecoveryService.Preview preview = recoverable[0];
            if (preview != null) {
                mutating.set(true);
                closeDialog.setDisable(true);
                advanced.setDisable(true);
                background(restore, recoveryStatus::setText, () -> {
                    maintenance.recover(preview);
                    controller.recoveryCompleted(preview.output());
                    return null;
                }, ignored -> {
                    recoverable[0] = null;
                    restore.setDisable(true);
                    recoveryStatus.setText(GuiText.get("normal.settings.restored"));
                }, "Restore previous translated copy", finishMutation);
            }
        });
        advanced.setOnAction(event -> {
            if (!mutating.get()) {
                dialog.close();
                openAdvanced.run();
            }
        });
        VBox content = new VBox(12, location, new HBox(8, previewCleanup, clean), cleanupStatus,
                restore, recoveryStatus, advanced);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private <T> void background(Button trigger, java.util.function.Consumer<String> result,
            Work<T> work,
            java.util.function.Consumer<T> success, String operation) {
        background(trigger, result, work, success, operation, () -> { });
    }

    private <T> void background(Button trigger, java.util.function.Consumer<String> result,
            Work<T> work, java.util.function.Consumer<T> success, String operation,
            Runnable finished) {
        trigger.setDisable(true);
        result.accept(GuiText.get("normal.working"));
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.run(); }
        };
        task.setOnSucceeded(event -> {
            trigger.setDisable(false);
            finished.run();
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            trigger.setDisable(false);
            finished.run();
            UserDiagnostic diagnostic = UserDiagnostic.failed(operation, task.getException());
            result.accept(diagnostic.summary() + "\n" + diagnostic.detail());
        });
        Thread.ofVirtual().name("project-go-maintenance").start(task);
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private String summary() {
        return controller.session().map(session -> {
            long total = session.project().entries().stream()
                    .filter(entry -> !entry.originalText().isBlank()).count();
            long translated = session.project().entries().stream()
                    .filter(entry -> !entry.originalText().isBlank() && !entry.translatedText().isBlank())
                    .count();
            long missing = total - translated;
            String progress = missing == 0
                    ? GuiText.get("normal.summary.complete").replace("{0}", Long.toString(total))
                    : GuiText.get("normal.summary.translated")
                            .replace("{0}", Long.toString(translated))
                            .replace("{1}", Long.toString(total)) + "\n"
                            + GuiText.get("normal.summary.missing")
                                    .replace("{0}", Long.toString(missing));
            return session.modName() + "\n" + languages(session) + "\n" + progress
                    + controller.notice().map(value -> "\n" + value).orElse("");
        }).orElse(GuiText.get("normal.chooseHelp"));
    }

    /** Readable language pair; a hidden or undetected source code is simply omitted. */
    private static String languages(TranslationWorkflow.Session session) {
        String target = session.presentation().targetLanguageName();
        String source = session.sourceLanguage();
        if (source == null || source.isBlank()) {
            return target;
        }
        return com.ssmt.project.PresentationNames.languageName(source) + " \u2192 " + target;
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
}
