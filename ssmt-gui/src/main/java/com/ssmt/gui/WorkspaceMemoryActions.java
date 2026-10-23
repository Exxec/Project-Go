package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import com.ssmt.tm.TranslationMemoryMergeResult;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Translation-memory selection, startup and merge actions for the advanced workspace. */
final class WorkspaceMemoryActions {
    private final AdvancedWorkspace owner;

    WorkspaceMemoryActions(AdvancedWorkspace owner) {
        this.owner = owner;
    }

    Optional<File> chooseTranslationMemory(Stage stage) {
        FileChooser chooser = owner.fileChooser();
        chooser.setTitle(GuiText.get("chooser.tm"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        GuiText.get("filter.sqlite"), "*.db", "*.sqlite"));
        return Optional.ofNullable(chooser.showSaveDialog(stage));
    }

    void openTranslationMemory(Stage stage) {
        FileChooser chooser = owner.fileChooser();
        chooser.setTitle(GuiText.get("chooser.tm"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        GuiText.get("filter.sqlite"), "*.db", "*.sqlite"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            owner.workspace.verifyTranslationMemory(selected.toPath());
            owner.activeTranslationMemory =
                    selected.toPath().toAbsolutePath().normalize();
            AdvancedWorkspace.rememberTranslationMemory(owner.activeTranslationMemory);
            owner.log(GuiText.get("status.tmOpened")
                    .replace("{0}", owner.activeTranslationMemory.toString()));
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic = UserDiagnostic.failed("Open translation memory", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        }
    }

    void initializeTranslationMemory() {
        Preferences preferences = Preferences.userNodeForPackage(SsmtApplication.class);
        String remembered = preferences.get(AdvancedWorkspace.MEMORY_PREFERENCE, "");
        String configured = System.getProperty("ssmt.catalog", "").strip();
        if (configured.isEmpty()) {
            configured = System.getenv().getOrDefault(
                    "SSMT_TRANSLATION_MEMORY", "").strip();
        }
        Path preferred = remembered.isBlank()
                ? DefaultTranslationMemoryLocator.sharedHeadlessDefault(
                        configured.isBlank() ? Optional.empty() : Optional.of(configured),
                        Optional.ofNullable(System.getenv("LOCALAPPDATA")),
                        Path.of(System.getProperty("user.home")))
                : Path.of(remembered).toAbsolutePath().normalize();
        try {
            Path parent = preferred.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            owner.openOrCreateTranslationMemory(preferred);
        } catch (java.io.IOException | ProjectException firstFailure) {
            Path fallback = DefaultTranslationMemoryLocator.resolve(
                    Optional.empty(),
                    Optional.ofNullable(System.getProperty("jpackage.app-path")),
                    Path.of(System.getProperty("user.dir", ".")));
            try {
                Path parent = fallback.getParent();
                if (parent != null) {
                    java.nio.file.Files.createDirectories(parent);
                }
                owner.openOrCreateTranslationMemory(fallback);
                owner.log(GuiText.get("status.tmFallback")
                        .replace("{0}", fallback.toString()));
            } catch (java.io.IOException | ProjectException secondFailure) {
                owner.activeTranslationMemory = null;
                owner.log(GuiText.get("error.tmUnavailable") + ": "
                        + secondFailure.getMessage());
            }
        }
    }

    void openOrCreateTranslationMemory(Path path) throws ProjectException {
        owner.workspace.openOrCreateTranslationMemory(path);
        owner.activeTranslationMemory = path.toAbsolutePath().normalize();
        AdvancedWorkspace.rememberTranslationMemory(owner.activeTranslationMemory);
        owner.log(GuiText.get("status.tmOpened")
                .replace("{0}", owner.activeTranslationMemory.toString()));
    }

    void compareAndMergeTranslationMemory(Stage stage) {
        if (owner.activeTranslationMemory == null) {
            owner.openTranslationMemory(stage);
        }
        if (owner.activeTranslationMemory == null) {
            return;
        }
        Alert direction = new Alert(
                Alert.AlertType.INFORMATION,
                GuiText.get("dialog.tmMerge.direction")
                        .replace("{destination}", owner.activeTranslationMemory.toString()),
                ButtonType.OK,
                ButtonType.CANCEL);
        direction.initOwner(stage);
        direction.setHeaderText(GuiText.get("dialog.tmMerge.selectHeader"));
        if (direction.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        FileChooser chooser = owner.fileChooser();
        chooser.setTitle(GuiText.get("chooser.tmMergeSource"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        GuiText.get("filter.sqlite"), "*.db", "*.sqlite"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        Path source = selected.toPath().toAbsolutePath().normalize();
        if (source.equals(owner.activeTranslationMemory)) {
            AdvancedWorkspace.showError(
                    GuiText.get("error.tmMerge"),
                    GuiText.get("error.tmMergeSame")
                            .replace("{path}", source.toString()));
            return;
        }
        try {
            TranslationMemoryMergeResult comparison =
                    owner.workspace.compareTranslationMemories(source, owner.activeTranslationMemory);
            String summary = AdvancedWorkspace.memoryMergeSummary(comparison);
            Alert confirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    summary,
                    ButtonType.YES,
                    ButtonType.NO);
            confirmation.initOwner(stage);
            confirmation.setHeaderText(GuiText.get("dialog.tmMerge.header"));
            if (comparison.changes() == 0
                    || confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                owner.log(GuiText.get("status.tmCompared") + ": " + summary);
                return;
            }
            TranslationMemoryMergeResult merged =
                    owner.workspace.mergeTranslationMemories(source, owner.activeTranslationMemory);
            owner.log(GuiText.get("status.tmMerged") + ": " + AdvancedWorkspace.memoryMergeSummary(merged));
        } catch (ProjectException exception) {
            UserDiagnostic diagnostic =
                    UserDiagnostic.failed("Compare/merge translation memory", exception);
            AdvancedWorkspace.showError(diagnostic.summary(), diagnostic.detail());
        }
    }
}
