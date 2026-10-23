package com.ssmt.gui;

import com.ssmt.ai.AiProviderType;
import com.ssmt.ai.TranslateLocallyPlugin;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Builds the existing providerSettingsTab view. */
final class ProviderSettingsTab {
    private ProviderSettingsTab() { }

    static Tab create(AdvancedWorkspace owner) {
        AiProviderSettingsViewModel model = new AiProviderSettingsViewModel();
        Preferences saved = Preferences.userNodeForPackage(SsmtApplication.class);
        ComboBox<AiProviderType> type = new ComboBox<>();
        type.getItems().addAll(AiProviderType.values());
        type.getSelectionModel().select(AiProviderType.valueOf(
                saved.get(AdvancedWorkspace.PROVIDER_TYPE, AiProviderType.OLLAMA.name())));
        TextField endpoint = new TextField(saved.get(
                AdvancedWorkspace.PROVIDER_ENDPOINT, GuiText.get("default.ollamaEndpoint")));
        TextField providerModel = new TextField(saved.get(
                AdvancedWorkspace.PROVIDER_MODEL, GuiText.get("default.ollamaModel")));
        TextField credentialVariable = new TextField(saved.get(AdvancedWorkspace.PROVIDER_CREDENTIAL, ""));
        credentialVariable.setPromptText(GuiText.get("placeholder.credential"));
        credentialVariable.setAccessibleText(GuiText.get("accessible.credential"));
        Button validate = new Button(GuiText.get("button.validateSettings"));
        Button preflight = new Button(GuiText.get("button.providerPreflight"));
        Label status = new Label(GuiText.get("status.noProvider"));
        validate.setAccessibleText(GuiText.get("accessible.validateProvider"));
        validate.setOnAction(ignored -> {
            try {
                model.update(
                        type.getValue(),
                        endpoint.getText(),
                        providerModel.getText(),
                        credentialVariable.getText());
                Preferences preferences = Preferences.userNodeForPackage(
                        SsmtApplication.class);
                preferences.put(AdvancedWorkspace.PROVIDER_TYPE, type.getValue().name());
                preferences.put(AdvancedWorkspace.PROVIDER_ENDPOINT, endpoint.getText().trim());
                preferences.put(AdvancedWorkspace.PROVIDER_MODEL, providerModel.getText().trim());
                preferences.put(AdvancedWorkspace.PROVIDER_CREDENTIAL, credentialVariable.getText().trim());
                status.setText(GuiText.get("status.providerValid"));
            } catch (IllegalArgumentException exception) {
                status.setText(exception.getMessage());
            }
        });
        preflight.setOnAction(ignored -> {
            List<String> findings = new com.ssmt.ai.LocalProviderPreflight().inspect(
                    Path.of("argos-translate"), Path.of("translateLocally"),
                    TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL);
            Alert report = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
            report.setHeaderText(GuiText.get("dialog.providerPreflight.title"));
            TextArea details = new TextArea(String.join(System.lineSeparator(), findings));
            details.setEditable(false);
            details.setWrapText(true);
            report.getDialogPane().setContent(details);
            report.showAndWait();
        });
        HBox controls = new HBox(
                8, type, endpoint, providerModel, credentialVariable, validate, preflight);
        return AdvancedWorkspace.fixedTab(GuiText.get("tab.provider"), new VBox(8, controls, status));
    }
}
