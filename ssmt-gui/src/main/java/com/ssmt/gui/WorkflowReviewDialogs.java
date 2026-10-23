package com.ssmt.gui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Normal-workflow glossary and review dialogs, including approval revocation. */
final class WorkflowReviewDialogs {
    private final TranslationWorkflowPane owner;

    WorkflowReviewDialogs(TranslationWorkflowPane owner) {
        this.owner = owner;
    }

    void showGlossaryDialog(com.ssmt.project.GlossaryService.Proposal proposal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner.stage);
        dialog.setTitle(GuiText.get("normal.glossary.title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox rows = new VBox(8);
        rows.setPadding(new Insets(8));
        rows.getChildren().add(new Label(GuiText.get("normal.glossary.approved").replace("{0}",
                Integer.toString(proposal.approved().size()))));
        proposal.approved().stream().limit(100).forEach(term -> rows.getChildren().add(new Label(
                term.source() + "  ->  " + term.target() + "   [" + term.origin() + "]")));
        rows.getChildren().add(new Label(GuiText.get("normal.glossary.proposed").replace("{0}",
                Integer.toString(proposal.proposed().size()))));
        for (var term : proposal.proposed().stream().limit(40).toList()) {
            javafx.scene.control.TextField target = new javafx.scene.control.TextField();
            target.setPromptText(GuiText.get("normal.glossary.prompt"));
            javafx.scene.control.CheckBox everyMod = new javafx.scene.control.CheckBox(
                    GuiText.get("normal.glossary.global"));
            Button approve = new Button(GuiText.get("normal.glossary.approve"));
            approve.setOnAction(event -> {
                if (target.getText().isBlank()) {
                    return;
                }
                dialog.close();
                owner.inspect(() -> {
                    owner.controller.approveGlossaryTerm(term.source(), target.getText().strip(), everyMod.isSelected());
                    return owner.controller.glossary();
                }, this::showGlossaryDialog, "Approve glossary term");
            });
            rows.getChildren().add(new VBox(2, new Label(term.source() + "   (" + term.occurrences() + "x, "
                    + term.note() + ")"), new HBox(8, target, everyMod, approve)));
        }
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(rows);
        scroll.setPrefViewportWidth(720);
        scroll.setPrefViewportHeight(480);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    void showText(String title, String text) {
        textDialog(title, text).showAndWait();
    }

    boolean showReview(String text) {
        Dialog<ButtonType> dialog = reviewDialog(text);
        return dialog.showAndWait().filter(result -> result != ButtonType.CLOSE).isPresent();
    }

    Dialog<ButtonType> reviewDialog(String text) {
        Dialog<ButtonType> dialog = textDialog(GuiText.get("normal.primary.review"), text);
        ButtonType revoke = new ButtonType(GuiText.get("normal.approval.revoke").replace("{0}",
                Long.toString(owner.controller.policyApprovedCount())), javafx.scene.control.ButtonBar.ButtonData.OTHER);
        if (owner.controller.policyApprovedCount() > 0) {
            dialog.getDialogPane().getButtonTypes().add(0, revoke);
        }
        dialog.setOnHidden(event -> {
            if (revoke.equals(dialog.getResult())) { owner.revokePolicyApprovals(); }
        });
        return dialog;
    }

    Dialog<ButtonType> textDialog(String title, String text) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner.stage);
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextArea content = new TextArea(text);
        content.setEditable(false);
        content.setWrapText(true);
        content.setPrefColumnCount(80);
        content.setPrefRowCount(22);
        dialog.getDialogPane().setContent(content);
        return dialog;
    }
}
