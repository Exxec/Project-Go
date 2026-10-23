package com.ssmt.gui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

/** Builds the existing projectInfoTab view. */
final class ProjectInfoTab {
    private ProjectInfoTab() { }

    static Tab create(AdvancedWorkspace owner) {
        VBox locations = new VBox(6);
        Label workflow = new Label();
        workflow.setWrapText(true);
        Runnable refreshInfo = () -> {
            locations.getChildren().clear();
            ProjectWorkspaceInfo info = owner.workspace.workspaceInfo(owner.activeTranslationMemory);
            owner.addPathRow(locations, GuiText.get("label.sourceRoot"), info.sourceRoot());
            owner.addPathRow(locations, GuiText.get("label.projectFile"), info.projectFile());
            owner.addPathRow(locations, GuiText.get("label.outputRoot"), info.outputRoot());
            owner.addPathRow(locations, GuiText.get("label.sourceBackupRoot"),
                    info.sourceBackupRoot());
            owner.addPathRow(locations, GuiText.get("label.translationMemory"),
                    info.translationMemory());
            owner.addPathRow(locations, GuiText.get("label.recoveryRoot"), info.recoveryRoot());
            owner.addPathRow(locations, GuiText.get("label.jsonSchema"), info.jsonSchemaCatalog());
            owner.addPathRow(locations, GuiText.get("label.csvSchema"), info.csvSchemaCatalog());
            workflow.setText(owner.workflowSummary(info));
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
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.projectInfo"), content);
    }
}
