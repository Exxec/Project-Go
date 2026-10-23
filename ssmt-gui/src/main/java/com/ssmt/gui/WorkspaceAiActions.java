package com.ssmt.gui;

import com.ssmt.ai.AiProviderType;
import com.ssmt.ai.AiProviderLocation;
import com.ssmt.ai.ArgosDevice;
import com.ssmt.ai.ArgosTranslatePlugin;
import com.ssmt.ai.OfflineTranslationChain;
import com.ssmt.ai.TranslateLocallyPlugin;
import com.ssmt.ai.TranslationMode;
import com.ssmt.ai.TranslationResourceLimits;
import com.ssmt.project.ProjectException;
import com.ssmt.project.AiTranslationImportResult;
import com.ssmt.project.AiImportPolicy;
import com.ssmt.project.ProjectSourceDetails;
import com.ssmt.project.ChainedProjectTranslationEngine;
import com.ssmt.project.PreferredLocalProvider;
import com.ssmt.project.ProjectTranslationSettings;
import com.ssmt.project.TranslationMemoryApprovedGlossary;
import com.ssmt.tm.SqliteTranslationMemory;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.prefs.Preferences;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Optional AI exchange, browser review and provider actions for the advanced workspace. */
final class WorkspaceAiActions {
    private final AdvancedWorkspace owner;

    WorkspaceAiActions(AdvancedWorkspace owner) {
        this.owner = owner;
    }

    void exportAiPackage(Stage stage) {
        if (owner.workspace.sourceRoot().isEmpty()) {
            AdvancedWorkspace.showError(GuiText.get("error.aiExport"), GuiText.get("error.noProject"));
            return;
        }
        owner.showAiExchangeGuidance(stage);
        String detectedLanguage;
        try {
            detectedLanguage = owner.workspace.detectSourceLanguage();
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Export for online AI", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        Optional<String> sourceLanguage = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("dialog.sourceLanguage"),
                GuiText.get("dialog.detectedLanguage.prompt"),
                detectedLanguage);
        Optional<String> targetLanguage = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("dialog.targetLanguage"),
                GuiText.get("dialog.englishTarget.prompt"),
                GuiText.get("default.aiTargetLanguage"));
        Optional<String> batch = AdvancedWorkspace.textInput(
                stage,
                GuiText.get("dialog.aiExportBatch.title"),
                GuiText.get("dialog.aiExportBatch.message"),
                "250");
        if (sourceLanguage.isEmpty() || targetLanguage.isEmpty() || batch.isEmpty()) {
            return;
        }
        owner.runProjectAction("Export for online AI", GuiText.get("status.aiExported"), () -> {
            ProjectSourceDetails details = owner.workspace.inspectSource(
                    owner.workspace.sourceRoot().orElseThrow());
            java.nio.file.Path destination = AdvancedWorkspace.artifactDirectory(
                    owner.workspace.sourceRoot().orElseThrow(),
                    owner.workspace.projectFile().orElse(null),
                    details.modName()).resolve(
                            AdvancedWorkspace.artifactBaseName(details.modName()) + " words.json");
            java.util.List<java.nio.file.Path> parts = owner.workspace.exportAiPackage(
                    destination,
                    details.modName(),
                    sourceLanguage.orElseThrow(),
                    targetLanguage.orElseThrow(),
                    Integer.parseInt(batch.orElseThrow().trim()));
            if (parts.size() > 1) {
                owner.log("Split across " + parts.size() + " file(s): "
                        + parts.stream()
                                .map(part -> String.valueOf(part.getFileName()))
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
        });
    }

    void translateProject(
            Stage stage, TableView<TranslationRow> table, Label status) {
        if (owner.workspace.projectFile().isEmpty()) {
            AdvancedWorkspace.showError(GuiText.get("error.translateProject"), GuiText.get("error.noProject"));
            return;
        }
        Optional<File> memory = owner.activeTranslationMemory == null
                ? owner.chooseTranslationMemory(stage)
                : Optional.of(owner.activeTranslationMemory.toFile());
        if (memory.isEmpty()) {
            return;
        }
        owner.activeTranslationMemory = memory.orElseThrow().toPath().toAbsolutePath().normalize();
        Optional<String> modeValue = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.translationMode.title"),
                GuiText.get("dialog.translationMode.message"), "SMART_DEFAULT");
        Optional<String> preferredValue = AdvancedWorkspace.textInput(stage,
                GuiText.get("dialog.preferredLocal.title"),
                GuiText.get("dialog.preferredLocal.message"), "ARGOS");
        Optional<String> batchValue = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.projectBatch.title"),
                GuiText.get("dialog.projectBatch.message"), "32");
        if (modeValue.isEmpty() || preferredValue.isEmpty() || batchValue.isEmpty()) {
            return;
        }
        try {
            TranslationMode mode = TranslationMode.valueOf(modeValue.get().trim().toUpperCase(
                    java.util.Locale.ROOT));
            PreferredLocalProvider preferred = PreferredLocalProvider.valueOf(
                    preferredValue.get().trim().toUpperCase(java.util.Locale.ROOT));
            Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
            String configuredType = preferences.get(AdvancedWorkspace.PROVIDER_TYPE, "");
            boolean remote = !configuredType.isBlank()
                    && AiProviderType.valueOf(configuredType) != AiProviderType.OLLAMA;
            boolean consent = false;
            if (remote && mode.allowsAi()) {
                Alert disclosure = new Alert(Alert.AlertType.CONFIRMATION,
                        GuiText.get("dialog.remoteAiConsent.message"),
                        ButtonType.YES, ButtonType.NO);
                disclosure.initOwner(stage);
                disclosure.setHeaderText(GuiText.get("dialog.remoteAiConsent.title"));
                consent = disclosure.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
                if (!consent) {
                    return;
                }
            }
            ProjectTranslationSettings settings = new ProjectTranslationSettings(
                    owner.workspace.detectSourceLanguage(), "en", mode, preferred, 1,
                    Integer.parseInt(batchValue.get()), OptionalLong.empty(), "", "", true,
                    consent);
            boolean remoteConsent = consent;
            com.ssmt.project.ProjectTranslationResult[] completed =
                    new com.ssmt.project.ProjectTranslationResult[1];
            owner.runBackgroundProjectAction(stage, GuiText.get("dialog.translateProject.title"),
                    GuiText.get("status.projectTranslated"), cancellation -> {
                        try (SqliteTranslationMemory tm = SqliteTranslationMemory.open(
                                owner.activeTranslationMemory)) {
                            var argos = new ArgosTranslatePlugin(
                                    Path.of("argos-translate"), ArgosDevice.CPU,
                                    new TranslationResourceLimits(
                                            1, settings.maximumBatchSize(), OptionalLong.empty()));
                            var locally = new TranslateLocallyPlugin(
                                    Path.of("translateLocally"),
                                    TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL);
                            OfflineTranslationChain offline = new OfflineTranslationChain(
                                    new TranslationMemoryApprovedGlossary(tm), argos, locally,
                                    preferred == PreferredLocalProvider.TRANSLATE_LOCALLY);
                            Optional<com.ssmt.ai.AiTranslationProvider> provider =
                                    AdvancedWorkspace.configuredProvider(preferences);
                            AiProviderLocation location = remote
                                    ? AiProviderLocation.REMOTE : AiProviderLocation.LOCAL;
                            completed[0] = owner.workspace.translateProject(settings,
                                    new ChainedProjectTranslationEngine(
                                            offline, settings, provider, location),
                                    cancellation);
                        } catch (com.ssmt.tm.TranslationMemoryException exception) {
                            throw new ProjectException(
                                    "Could not open translation memory", exception);
                        }
                    }, () -> {
                        owner.refreshEditor(table);
                        owner.updateStatus(status);
                        if (completed[0] != null) {
                            owner.log(GuiText.get("status.translationBackends")
                                    .replace("{0}", String.join(", ",
                                            completed[0].backendsUsed()))
                                    .replace("{1}", Integer.toString(
                                            completed[0].unresolvedEntries())));
                        }
                        if (remoteConsent) {
                            owner.log(GuiText.get("status.remoteAiUsed"));
                        }
                    });
        } catch (IllegalArgumentException | ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Translate project", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    void exportBrowserReview(Stage stage) {
        if (owner.workspace.sourceRoot().isEmpty()) {
            AdvancedWorkspace.showError(GuiText.get("error.aiExport"), GuiText.get("error.noProject"));
            return;
        }
        Alert privacy = new Alert(Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.browserAiPrivacy.message"),
                ButtonType.OK, ButtonType.CANCEL);
        privacy.initOwner(stage);
        privacy.setHeaderText(GuiText.get("dialog.browserAiPrivacy.title"));
        if (privacy.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            ProjectSourceDetails details = owner.workspace.inspectSource(
                    owner.workspace.sourceRoot().orElseThrow());
            String detected = owner.workspace.detectSourceLanguage();
            Optional<String> source = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.sourceLanguage"),
                    GuiText.get("dialog.detectedLanguage.prompt"), detected);
            Optional<String> target = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.targetLanguage"),
                    GuiText.get("dialog.englishTarget.prompt"), "en");
            Optional<String> batch = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.browserAiBatch.title"),
                    GuiText.get("dialog.browserAiBatch.message"), "250");
            Optional<String> terms = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.browserAiTerms.title"),
                    GuiText.get("dialog.browserAiTerms.message"), "");
            if (source.isEmpty() || target.isEmpty() || batch.isEmpty() || terms.isEmpty()) {
                return;
            }
            Path destination = AdvancedWorkspace.artifactDirectory(owner.workspace.sourceRoot().orElseThrow(),
                    owner.workspace.projectFile().orElse(null), details.modName())
                    .resolve(AdvancedWorkspace.artifactBaseName(details.modName()) + " browser AI review");
            owner.workspace.exportBrowserReview(destination, details.modName(), source.get(),
                    target.get(), Integer.parseInt(batch.get()), terms.get());
            Preferences.userNodeForPackage(SsmtApplication.class)
                    .put(AdvancedWorkspace.BROWSER_AI_EXPORT, destination.toAbsolutePath().normalize().toString());
            owner.log(GuiText.get("status.browserAiExported"));
        } catch (ProjectException | IllegalArgumentException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Export browser AI review", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    void openConfiguredAiWebsite(Stage stage) {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        Optional<String> name = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.browserAiProvider.title"),
                GuiText.get("dialog.browserAiProvider.message"),
                preferences.get(AdvancedWorkspace.BROWSER_AI_NAME, "ChatGPT"));
        if (name.isEmpty()) {
            return;
        }
        String preset = switch (name.get().trim().toLowerCase(java.util.Locale.ROOT)) {
            case "claude" -> "https://claude.ai/";
            case "gemini" -> "https://gemini.google.com/";
            default -> preferences.get(AdvancedWorkspace.BROWSER_AI_URL, "https://chatgpt.com/");
        };
        Optional<String> url = AdvancedWorkspace.textInput(stage, GuiText.get("dialog.browserAiUrl.title"),
                GuiText.get("dialog.browserAiUrl.message"), preset);
        if (url.isEmpty()) {
            return;
        }
        try {
            URI uri = URI.create(url.get().trim());
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Only HTTP or HTTPS AI website URLs are allowed");
            }
            preferences.put(AdvancedWorkspace.BROWSER_AI_NAME, name.get().trim());
            preferences.put(AdvancedWorkspace.BROWSER_AI_URL, uri.toString());
            owner.getHostServices().showDocument(uri.toString());
        } catch (IllegalArgumentException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Open AI review website", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    void copyBrowserPrompt() {
        owner.lastBrowserExport().ifPresentOrElse(folder -> {
            try {
                ClipboardContent content = new ClipboardContent();
                content.putString(Files.readString(folder.resolve("PROMPT.txt")));
                Clipboard.getSystemClipboard().setContent(content);
                owner.log(GuiText.get("status.browserAiPromptCopied"));
            } catch (IOException exception) {
                UserDiagnostic diagnostic = UserDiagnostic.failed("Copy browser AI prompt", exception);
                AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            }
        }, () -> AdvancedWorkspace.showError(GuiText.get("error.browserAi"),
                GuiText.get("error.browserAiNoExport")));
    }

    void openLastBrowserExport() {
        owner.lastBrowserExport().ifPresentOrElse(owner::openFolder,
                () -> AdvancedWorkspace.showError(GuiText.get("error.browserAi"),
                        GuiText.get("error.browserAiNoExport")));
    }

    void reopenLastBrowserExport() {
        owner.openLastBrowserExport();
    }

    Optional<Path> lastBrowserExport() {
        String value = Preferences.userNodeForPackage(SsmtApplication.class)
                .get(AdvancedWorkspace.BROWSER_AI_EXPORT, "");
        return value.isBlank() ? Optional.empty() : Optional.of(Path.of(value));
    }

    void importBrowserReview(
            Stage stage, TableView<TranslationRow> table, Label status) {
        FileChooser chooser = owner.fileChooser();
        chooser.setTitle(GuiText.get("chooser.browserAiImport"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GuiText.get("filter.json"), "*.json"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        owner.runProjectAction("Import browser AI review", GuiText.get("status.browserAiImported"), () -> {
            owner.workspace.importBrowserReview(selected.toPath());
            owner.refreshEditor(table);
            owner.updateStatus(status);
        });
    }

    void showAiExchangeGuidance(Stage stage) {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        if (preferences.getBoolean(AdvancedWorkspace.AI_GUIDANCE_PREFERENCE, false)) {
            return;
        }
        Alert guidance = new Alert(
                Alert.AlertType.INFORMATION,
                GuiText.get("dialog.aiGuidance.message"),
                ButtonType.OK);
        guidance.initOwner(stage);
        guidance.setHeaderText(GuiText.get("dialog.aiGuidance.title"));
        guidance.showAndWait();
        preferences.putBoolean(AdvancedWorkspace.AI_GUIDANCE_PREFERENCE, true);
    }

    void importAiResponse(
            Stage stage,
            TableView<TranslationRow> table,
            Label status) {
        ProjectSourceDetails details;
        try {
            details = owner.workspace.inspectSource(owner.workspace.sourceRoot().orElseThrow());
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Import AI response", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
            return;
        }
        java.nio.file.Path response = AdvancedWorkspace.artifactDirectory(
                owner.workspace.sourceRoot().orElseThrow(),
                owner.workspace.projectFile().orElse(null),
                details.modName()).resolve(
                        AdvancedWorkspace.artifactBaseName(details.modName())
                                + " words translated.json");
        if (!java.nio.file.Files.isRegularFile(response)) {
            FileChooser chooser = owner.fileChooser();
            chooser.setTitle(GuiText.get("chooser.aiImport"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            GuiText.get("filter.json"), "*.json"));
            chooser.setInitialFileName(java.util.Objects.requireNonNull(
                    response.getFileName(), "AI response filename").toString());
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) {
                return;
            }
            response = selected.toPath();
        }
        java.nio.file.Path responsePath = response;
        Alert memoryChoice = new Alert(
                Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.aiMemory.message"),
                ButtonType.YES,
                ButtonType.NO,
                ButtonType.CANCEL);
        memoryChoice.initOwner(stage);
        memoryChoice.setHeaderText(GuiText.get("dialog.aiMemory.title"));
        ButtonType choice = memoryChoice.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == ButtonType.CANCEL) {
            return;
        }
        Optional<File> memory = choice == ButtonType.YES
                ? owner.activeTranslationMemory == null
                        ? owner.chooseTranslationMemory(stage)
                        : Optional.of(owner.activeTranslationMemory.toFile())
                : Optional.empty();
        if (choice == ButtonType.YES && memory.isEmpty()) {
            return;
        }
        memory.map(File::toPath).ifPresent(path ->
                owner.activeTranslationMemory = path.toAbsolutePath().normalize());
        Alert approvalChoice = new Alert(
                Alert.AlertType.CONFIRMATION,
                GuiText.get("dialog.aiApproval.message"),
                ButtonType.YES,
                ButtonType.NO,
                ButtonType.CANCEL);
        approvalChoice.initOwner(stage);
        approvalChoice.setHeaderText(GuiText.get("dialog.aiApproval.title"));
        ButtonType approval = approvalChoice.showAndWait().orElse(ButtonType.CANCEL);
        if (approval == ButtonType.CANCEL) {
            return;
        }
        AiImportPolicy policy = approval == ButtonType.YES
                ? AiImportPolicy.APPROVE_ALL_VALIDATED
                : AiImportPolicy.REVIEW_DRAFTS;
        owner.runProjectAction("Import AI response", GuiText.get("status.aiImported"), () -> {
            AiTranslationImportResult result = owner.workspace.importAiResponse(
                    responsePath,
                    memory.map(File::toPath).orElse(null),
                    policy);
            owner.refreshEditor(table);
            owner.updateStatus(status);
            owner.log(GuiText.get("status.aiImportCount")
                    .replace("{0}", Integer.toString(result.importedEntries())));
        });
    }
}
