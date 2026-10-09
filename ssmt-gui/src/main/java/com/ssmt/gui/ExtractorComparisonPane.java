package com.ssmt.gui;

import com.ssmt.project.BridgeForgeTranslationComparison;
import com.ssmt.project.BridgeForgeTranslationDocument;
import java.io.File;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Advanced adapter for the existing read-only comparison contract. */
final class ExtractorComparisonPane extends VBox {
    private Path source;
    private Path reference;
    private final Label selected = new Label("Choose the same mod tree used for the BridgeForge export.");
    private final TextArea result = new TextArea();

    ExtractorComparisonPane(Stage stage) {
        super(12);
        setPadding(new Insets(16));
        Button chooseSource = new Button("Choose Mod Folder");
        Button chooseReference = new Button("Choose BridgeForge Export");
        Button compare = new Button("Compare Extractors");
        result.setEditable(false);
        result.setWrapText(true);
        selected.setWrapText(true);
        chooseSource.setOnAction(event -> {
            File folder = new DirectoryChooser().showDialog(stage);
            if (folder != null) {
                source = folder.toPath();
                updateSelection();
            }
        });
        chooseReference.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BridgeForge export JSON", "*.json"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                reference = file.toPath();
                updateSelection();
            }
        });
        compare.setOnAction(event -> {
            if (source == null || reference == null) {
                result.setText("Choose both a mod folder and a reference export.");
                return;
            }
            Path chosenSource = source;
            Path chosenReference = reference;
            setDisable(true);
            result.setText("Comparing observed units and checking reference source hashes...");
            Task<String> task = new Task<>() {
                @Override protected String call() throws Exception {
                    var report = new BridgeForgeTranslationComparison().compare(chosenSource,
                            new BridgeForgeTranslationDocument().read(chosenReference));
                    var groups = new java.util.TreeMap<String, java.util.List<String>>();
                    for (var difference : report.path("differences")) {
                        groups.computeIfAbsent(difference.path("file").asText(), ignored -> new java.util.ArrayList<>())
                                .add(difference.path("reason").asText() + ": " + difference.path("id").asText()
                                        + " " + difference.path("fields"));
                    }
                    StringBuilder text = new StringBuilder(report.path("status").asText()).append("\n")
                            .append(report.path("scope").asText()).append("\n")
                            .append("Source mismatches: ").append(report.path("source_mismatches")).append("\n")
                            .append("Project Go unreadable: ").append(report.path("project_go_unreadable")).append("\n")
                            .append("Reference unreadable: ").append(report.path("reference_unreadable")).append("\n");
                    groups.forEach((file, findings) -> text.append("\n").append(file).append("\n")
                            .append(String.join("\n", findings)).append("\n"));
                    return text.toString();
                }
            };
            task.setOnSucceeded(done -> {
                setDisable(false);
                result.setText(task.getValue());
            });
            task.setOnFailed(done -> {
                setDisable(false);
                result.setText(UserDiagnostic.failed("Compare extractors", task.getException()).detail());
            });
            Thread.ofVirtual().name("project-go-comparison").start(task);
        });
        getChildren().addAll(selected, chooseSource, chooseReference, compare, result);
    }

    private void updateSelection() {
        selected.setText("Mod: " + source + "\nReference: " + reference);
    }
}
