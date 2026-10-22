package com.ssmt.gui;

import com.ssmt.project.GuiChecklistReview;
import com.ssmt.project.GuiChecklistReview.Row;
import com.ssmt.project.GuiChecklistReview.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Review inbox for the GUI visual checklist: lists only the rows that are unreviewed or whose evidence changed, shows
 * each row's expectation, screenshots and prior decision, and records an owner decision through
 * {@link GuiChecklistReview#record}. Nothing is recorded except by the owner pressing PASS, FAIL or NOT_TESTED.
 */
final class ReviewInboxPane extends BorderPane {
    private static final String DEFAULT_FOLDER = "ssmt-gui/build/gui-checklist";
    private static final double SHOT_WIDTH = 640;

    private final TextField folder = new TextField(DEFAULT_FOLDER);
    private final ListView<Row> inbox = new ListView<>();
    private final Label summary = new Label();
    private final VBox detail = new VBox(8);
    private final TextArea note = new TextArea();
    private final Label message = new Label();
    private final Button pass = new Button("PASS");
    private final Button fail = new Button("FAIL");
    private final Button notTested = new Button("NOT_TESTED");

    ReviewInboxPane() {
        Button load = new Button("Load");
        load.setOnAction(ignored -> reload());
        folder.setAccessibleText("Checklist folder");
        HBox top = new HBox(8, new Label("Checklist folder:"), folder, load);
        HBox.setHgrow(folder, Priority.ALWAYS);
        top.setPadding(new Insets(8));
        inbox.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : row.number() + ". " + row.scenario() + "  [" + row.state() + "]");
            }
        });
        inbox.getSelectionModel().selectedItemProperty().addListener((observable, before, row) -> show(row));
        note.setPromptText("Observation (required for FAIL)");
        note.setPrefRowCount(3);
        pass.setOnAction(ignored -> decide(Status.PASS));
        fail.setOnAction(ignored -> decide(Status.FAIL));
        notTested.setOnAction(ignored -> decide(Status.NOT_TESTED));
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detail.setPadding(new Insets(8));
        VBox left = new VBox(4, summary, inbox);
        VBox.setVgrow(inbox, Priority.ALWAYS);
        left.setPadding(new Insets(8));
        left.setPrefWidth(320);
        setTop(top);
        setLeft(left);
        setCenter(detailScroll);
        setBottom(new VBox(4, note, new HBox(8, pass, fail, notTested, message)));
        show(null);
    }

    /** Loads the folder named in the text field; shows only the rows that need review. */
    void reload() {
        try {
            List<Row> rows = new GuiChecklistReview(Path.of(folder.getText().strip())).inbox();
            inbox.getItems().setAll(rows);
            summary.setText(rows.size() + " rows need review");
            message.setText("");
            if (!rows.isEmpty()) {
                inbox.getSelectionModel().select(0);
            } else {
                show(null);
            }
        } catch (IOException | RuntimeException exception) {
            inbox.getItems().clear();
            summary.setText("");
            show(null);
            message.setText("Could not load: " + exception.getMessage());
        }
    }

    void setFolder(Path path) {
        folder.setText(path.toString());
    }

    int inboxSize() {
        return inbox.getItems().size();
    }

    void select(int index) {
        inbox.getSelectionModel().select(index);
    }

    void setNote(String text) {
        note.setText(text);
    }

    /** Records the owner's decision for the selected row; the only path that writes a receipt. */
    void decide(Status status) {
        Row row = inbox.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        try {
            new GuiChecklistReview(Path.of(folder.getText().strip())).record(row.number(), status, note.getText());
            note.clear();
            reload();
            message.setText("Recorded row " + row.number() + ": " + status);
        } catch (IOException | RuntimeException exception) {
            message.setText("Not recorded: " + exception.getMessage());
        }
    }

    private void show(Row row) {
        detail.getChildren().clear();
        boolean selected = row != null;
        pass.setDisable(!selected);
        fail.setDisable(!selected);
        notTested.setDisable(!selected);
        if (!selected) {
            detail.getChildren().add(new Label("Select a row to review."));
            return;
        }
        Label title = new Label(row.number() + ". " + row.scenario());
        title.setStyle("-fx-font-weight: bold;");
        title.setWrapText(true);
        detail.getChildren().addAll(title, wrapped("Expected: " + row.expected()),
                wrapped("Program result: " + row.automated() + " - " + row.observed()),
                wrapped(row.decision().map(decision -> "Prior owner decision: " + decision.status() + " at "
                        + decision.decidedAt() + (decision.note().isEmpty() ? "" : " - " + decision.note())
                        + (row.state() == GuiChecklistReview.State.EVIDENCE_CHANGED
                                ? " (evidence has changed since)" : "")).orElse("Prior owner decision: none")));
        for (Path shot : row.screenshots()) {
            detail.getChildren().add(new Label(String.valueOf(shot.getFileName())));
            if (Files.isRegularFile(shot)) {
                ImageView view = new ImageView(new Image(shot.toUri().toString()));
                view.setPreserveRatio(true);
                view.setFitWidth(SHOT_WIDTH);
                detail.getChildren().add(view);
            } else {
                detail.getChildren().add(new Label("(screenshot file is missing)"));
            }
        }
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
