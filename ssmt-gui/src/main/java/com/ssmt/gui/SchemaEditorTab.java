package com.ssmt.gui;

import java.io.File;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Builds the existing schemaEditorTab view. */
final class SchemaEditorTab {
    private SchemaEditorTab() { }

    static Tab create(AdvancedWorkspace owner, Stage stage) {
        JsonSchemaEditorViewModel model = new JsonSchemaEditorViewModel();
        TextField path = new TextField(GuiText.get("default.schemaPath"));
        path.setPromptText(GuiText.get("placeholder.schemaPath"));
        path.setAccessibleText(GuiText.get("accessible.schemaPath"));
        TextField pointer = new TextField(GuiText.get("default.schemaPointer"));
        pointer.setPromptText(GuiText.get("placeholder.pointer"));
        pointer.setAccessibleText(GuiText.get("accessible.schemaPointer"));
        TextArea summary = AdvancedWorkspace.preview(GuiText.get("placeholder.schema"));
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
                AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        Button save = new Button(GuiText.get("button.saveSchema"));
        save.setAccessibleText(GuiText.get("accessible.saveSchema"));
        save.setOnAction(ignored -> {
            FileChooser chooser = owner.fileChooser();
            chooser.setTitle(GuiText.get("chooser.schemaSave"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            GuiText.get("filter.schema"), "*.json"));
            File destination = chooser.showSaveDialog(stage);
            if (destination != null) {
                try {
                    model.save(destination.toPath());
                    owner.log(GuiText.get("status.schemaSaved"));
                } catch (com.ssmt.core.exception.SsmtParseException exception) {
                    UserDiagnostic diagnostic = UserDiagnostic.failed("Save schema", exception);
                    AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
                }
            }
        });
        HBox controls = new HBox(8, path, pointer, add, save);
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.schema"), new VBox(8, controls, summary));
    }
}
