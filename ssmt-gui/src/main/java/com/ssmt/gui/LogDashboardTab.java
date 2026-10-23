package com.ssmt.gui;

import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

/** Builds the existing logTab view. */
final class LogDashboardTab {
    private LogDashboardTab() { }

    static Tab create(AdvancedWorkspace owner) {
        owner.logTable = new TableView<>();
        owner.logTable.setPlaceholder(new Label(GuiText.get("placeholder.diagnostics")));
        owner.logTable.getColumns().add(AdvancedWorkspace.column(
                GuiText.get("column.time"), row -> AdvancedWorkspace.LOG_TIME.format(row.timestamp())));
        owner.logTable.getColumns().add(AdvancedWorkspace.column(
                GuiText.get("column.level"), row -> row.level().name()));
        owner.logTable.getColumns().add(AdvancedWorkspace.column(
                GuiText.get("column.message"), LogEntry::message));
        owner.refreshLogs();
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.diagnostics"), owner.logTable);
    }
}
