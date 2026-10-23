package com.ssmt.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** JavaFX application shell; views and workspace actions live in their owning classes. */
public final class SsmtApplication extends Application {
    public static final String WINDOW_TITLE = GuiText.get("window.title");
    private final AdvancedWorkspace advancedWorkspace = new AdvancedWorkspace(this::getHostServices);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Tab advanced = advancedWorkspace.create(stage);
        TabPane modes = new TabPane();
        Tab normal = AdvancedWorkspace.fixedTab(GuiText.get("normal.title"),
                new TranslationWorkflowPane(stage,
                        () -> modes.getSelectionModel().select(advanced)));
        modes.getTabs().addAll(normal, advanced);
        BorderPane root = new BorderPane(modes);
        root.setPadding(new Insets(8));
        stage.setTitle(WINDOW_TITLE);
        stage.getIcons().add(new Image(java.util.Objects.requireNonNull(
                        SsmtApplication.class.getResource("ssmt-icon.png"))
                .toExternalForm()));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(new Scene(root, 1200, 760));
        stage.setOnCloseRequest(event -> {
            if (!advancedWorkspace.hasUnsavedChanges()) {
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
    }

    static java.nio.file.Path artifactDirectory(java.nio.file.Path source,
            java.nio.file.Path project, String modName) {
        return AdvancedWorkspace.artifactDirectory(source, project, modName);
    }

    static String artifactBaseName(String modName) {
        return AdvancedWorkspace.artifactBaseName(modName);
    }
}
