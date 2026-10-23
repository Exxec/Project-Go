package com.ssmt.gui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Builds the existing welcomeTab view. */
final class WelcomeTab {
    private WelcomeTab() { }

    static Tab create(AdvancedWorkspace owner, Stage stage) {
        Label title = new Label(GuiText.get("heading.welcome"));
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label introduction = new Label(GuiText.get("label.welcomeIntroduction"));
        introduction.setWrapText(true);

        SplitMenuButton start = new SplitMenuButton();
        start.setText(GuiText.get("button.create"));
        start.setAccessibleText(GuiText.get("accessible.create"));
        start.setOnAction(ignored -> owner.createProject(
                stage, owner.editorTable, owner.editorStatus, AdvancedWorkspace.SchemaKind.NONE, owner::showEditor));
        start.getItems().addAll(
                AdvancedWorkspace.schemaVariantMenuItem(
                        GuiText.get("button.createSchema"),
                        GuiText.get("accessible.createSchema"),
                        ignored -> owner.createProject(
                                stage, owner.editorTable, owner.editorStatus, AdvancedWorkspace.SchemaKind.JSON, owner::showEditor)),
                AdvancedWorkspace.schemaVariantMenuItem(
                        GuiText.get("button.createCsvSchema"),
                        GuiText.get("accessible.createCsvSchema"),
                        ignored -> owner.createProject(
                                stage, owner.editorTable, owner.editorStatus, AdvancedWorkspace.SchemaKind.CSV, owner::showEditor)));
        Button open = new Button(GuiText.get("button.open"));
        open.setAccessibleText(GuiText.get("accessible.open"));
        open.setOnAction(ignored -> owner.openProject(
                stage, owner.editorTable, owner.editorStatus, owner::showEditor));
        Button sample = new Button(GuiText.get("button.sample"));
        sample.setAccessibleText(GuiText.get("accessible.sample"));
        sample.setOnAction(ignored -> owner.openSampleProject(
                stage, owner.editorTable, owner.editorStatus, owner::showEditor));
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
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.start"), content);
    }
}
