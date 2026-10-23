package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import java.io.File;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Builds the existing fontCoverageTab view. */
final class FontCoverageTab {
    private FontCoverageTab() { }

    static Tab create(AdvancedWorkspace owner, Stage stage) {
        owner.fontCoverageTable = new TableView<>();
        owner.fontCoverageTable.setPlaceholder(new Label(GuiText.get("placeholder.fontCoverage")));
        owner.fontCoverageTable.getColumns().add(AdvancedWorkspace.column(
                GuiText.get("column.file"), row -> row.sourceFile().toString()));
        owner.fontCoverageTable.getColumns().add(AdvancedWorkspace.column(
                GuiText.get("column.key"), row -> row.key()));
        owner.fontCoverageTable.getColumns().add(AdvancedWorkspace.column(
                GuiText.get("column.missing"), row -> row.missingCharacters()));
        Button checkFont = new Button(GuiText.get("button.checkFontCoverage"));
        checkFont.setAccessibleText(GuiText.get("accessible.checkFontCoverage"));
        checkFont.setOnAction(event -> {
            FileChooser chooser = owner.fileChooser();
            chooser.setTitle(GuiText.get("chooser.fontOpen"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(GuiText.get("filter.font"), "*.fnt"));
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) {
                return;
            }
            try {
                List<com.ssmt.project.FontCoverageFinding> findings =
                        owner.workspace.checkFontCoverage(selected.toPath());
                owner.fontCoverageTable.setItems(FXCollections.observableArrayList(findings));
                owner.log(GuiText.get("status.fontCoverageChecked")
                        .replace("{0}", Integer.toString(findings.size())));
            } catch (ProjectException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Check font coverage", exception);
                AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            }
        });
        VBox content = new VBox(8, new HBox(8, checkFont), owner.fontCoverageTable);
        content.setPadding(new Insets(12));
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.fontCoverage"), content);
    }
}
