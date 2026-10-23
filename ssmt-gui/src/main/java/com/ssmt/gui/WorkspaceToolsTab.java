package com.ssmt.gui;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

/** Builds the existing toolsTab view. */
final class WorkspaceToolsTab {
    private WorkspaceToolsTab() { }

    static Tab create(AdvancedWorkspace owner, Stage stage) {
        TabPane tools = new TabPane(
                owner.projectInfoTab(),
                owner.schemaEditorTab(stage),
                owner.providerSettingsTab(),
                owner.fontCoverageTab(stage),
                AdvancedWorkspace.fixedTab("Extractor Comparison", new ExtractorComparisonPane(stage)),
                AdvancedWorkspace.fixedTab("Review Inbox", new ReviewInboxPane()),
                owner.logTab());
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.tools"), tools);
    }
}
