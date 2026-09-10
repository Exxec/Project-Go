package com.ssmt.gui;

import com.ssmt.project.TranslationWorkflow;
import java.io.File;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Three-stage normal flow; the old editor and configuration remain in Advanced. */
final class TranslationWorkflowPane extends VBox {
    private final TranslationWorkflowController controller =
            new TranslationWorkflowController(new TranslationWorkflow());
    private final Label status = new Label(GuiText.get("normal.chooseHelp"));
    private final Label prompt = new Label();
    private final Label[] stages = new Label[3];
    private final Button primary = new Button();
    private final MenuButton otherActions = new MenuButton(GuiText.get("normal.otherActions"));
    private final TranslationWorkflowPresentation presentation = new TranslationWorkflowPresentation();
    private final Stage stage;

    TranslationWorkflowPane(Stage stage) {
        super(16);
        this.stage = stage;
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
        MenuItem choose = menuItem(GuiText.get("normal.action.changeMod"), this::chooseMod);
        MenuItem export = menuItem(GuiText.get("normal.action.exportAgain"), this::exportTranslation);
        MenuItem importFile = menuItem(GuiText.get("normal.action.importFile"), this::importTranslation);
        MenuItem build = menuItem(GuiText.get("normal.action.buildNow"), this::buildCopy);
        otherActions.getItems().addAll(choose, export, importFile, build);
        getChildren().addAll(title, explanation, new HBox(20, stages), prompt,
                new HBox(10, primary, otherActions), status);
        update();
    }

    private void chooseMod() {
        DirectoryChooser picker = new DirectoryChooser();
        picker.setTitle(GuiText.get("normal.primary.choose"));
        File folder = picker.showDialog(stage);
        if (folder != null) {
            run(TranslationWorkflowPresentation.Action.CHOOSE_MOD,
                    () -> controller.loadMod(folder.toPath()), GuiText.get("normal.loaded"));
        }
    }

    private void exportTranslation() {
        File file = jsonChooser("translation.json").showSaveDialog(stage);
        if (file != null) {
            run(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION,
                    () -> controller.exportTranslation(file.toPath()), GuiText.get("normal.exported"));
        }
    }

    private void importTranslation() {
        File file = jsonChooser("translation.json").showOpenDialog(stage);
        if (file != null) {
            run(TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION,
                    () -> controller.importTranslation(file.toPath()), GuiText.get("normal.imported"));
        }
    }

    private void buildCopy() {
        DirectoryChooser picker = new DirectoryChooser();
        picker.setTitle(GuiText.get("normal.output"));
        File folder = picker.showDialog(stage);
        if (folder != null) {
            String id = controller.session().orElseThrow().project().sourceModId()
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            Path output = folder.toPath().resolve(id + "-translated");
            run(TranslationWorkflowPresentation.Action.BUILD_COPY,
                    () -> controller.buildPatch(output), GuiText.get("normal.built") + " " + output);
        }
    }

    private static FileChooser jsonChooser(String name) {
        FileChooser picker = new FileChooser();
        picker.setInitialFileName(name);
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Translation JSON", "*.json"));
        return picker;
    }

    private void run(TranslationWorkflowPresentation.Action completed, Action action, String success) {
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
        task.setOnFailed(event -> {
            setDisable(false);
            update();
            status.setText(task.getException().getMessage() + "\n" + summary());
        });
        Thread.ofVirtual().name("project-go-workflow").start(task);
    }

    private boolean readyToBuild() {
        return controller.session().map(session -> session.project().entries().stream()
                .filter(entry -> !entry.originalText().isBlank())
                .allMatch(entry -> !entry.translatedText().isBlank())).orElse(false);
    }

    private void update() {
        boolean missing = controller.session().isEmpty();
        TranslationWorkflowPresentation.Action action = presentation.action();
        primary.setText(action.buttonText());
        primary.setOnAction(event -> perform(action));
        primary.setDefaultButton(true);
        prompt.setText(action.prompt());
        otherActions.setDisable(missing);
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
        }
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private String summary() {
        return controller.session().map(session -> {
            long translated = session.project().entries().stream().filter(e -> !e.translatedText().isBlank()).count();
            return session.modName() + " — " + translated + " / " + session.project().entries().size()
                    + " " + GuiText.get("normal.translated") + "; " + session.needsReview()
                    + " " + GuiText.get("normal.review");
        }).orElse(GuiText.get("normal.chooseHelp"));
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
}
