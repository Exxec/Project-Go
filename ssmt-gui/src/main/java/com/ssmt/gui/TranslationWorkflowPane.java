package com.ssmt.gui;

import com.ssmt.project.TranslationWorkflow;
import java.io.File;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Four normal actions; the old editor and configuration remain in Advanced. */
final class TranslationWorkflowPane extends VBox {
    private final TranslationWorkflowController controller =
            new TranslationWorkflowController(new TranslationWorkflow());
    private final Label status = new Label(GuiText.get("normal.chooseHelp"));
    private final Button export = new Button(GuiText.get("normal.export"));
    private final Button importFile = new Button(GuiText.get("normal.import"));
    private final Button build = new Button(GuiText.get("normal.build"));

    TranslationWorkflowPane(Stage stage) {
        super(16);
        setPadding(new Insets(24));
        Label title = new Label(GuiText.get("normal.title"));
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label explanation = new Label(GuiText.get("normal.explanation"));
        explanation.setWrapText(true);
        status.setWrapText(true);
        Button choose = new Button(GuiText.get("normal.choose"));
        choose.setOnAction(event -> {
            DirectoryChooser picker = new DirectoryChooser();
            picker.setTitle(GuiText.get("normal.choose"));
            File folder = picker.showDialog(stage);
            if (folder != null) { run(() -> controller.loadMod(folder.toPath()), GuiText.get("normal.loaded")); }
        });
        export.setOnAction(event -> {
            File file = jsonChooser("translation.json").showSaveDialog(stage);
            if (file != null) { run(() -> controller.exportTranslation(file.toPath()), GuiText.get("normal.exported")); }
        });
        importFile.setOnAction(event -> {
            File file = jsonChooser("translation.json").showOpenDialog(stage);
            if (file != null) { run(() -> controller.importTranslation(file.toPath()), GuiText.get("normal.imported")); }
        });
        build.setOnAction(event -> {
            DirectoryChooser picker = new DirectoryChooser();
            picker.setTitle(GuiText.get("normal.output"));
            File folder = picker.showDialog(stage);
            if (folder != null) {
                String id = controller.session().orElseThrow().project().sourceModId()
                        .replaceAll("[^A-Za-z0-9._-]", "_");
                Path output = folder.toPath().resolve(id + "-translated");
                run(() -> controller.buildPatch(output), GuiText.get("normal.built") + " " + output);
            }
        });
        getChildren().addAll(title, explanation, choose, export, importFile, build, status);
        update();
    }

    private static FileChooser jsonChooser(String name) {
        FileChooser picker = new FileChooser();
        picker.setInitialFileName(name);
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Translation JSON", "*.json"));
        return picker;
    }

    private void run(Action action, String success) {
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

    private void update() {
        boolean missing = controller.session().isEmpty();
        export.setDisable(missing);
        importFile.setDisable(missing);
        build.setDisable(missing);
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
