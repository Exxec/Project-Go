package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.GuiChecklistReview.State;
import com.ssmt.project.GuiChecklistReview.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiChecklistReviewTest {
    @TempDir Path directory;
    private GuiChecklistReview review;

    @BeforeEach void checklistWithThreeRows() throws Exception {
        Files.writeString(directory.resolve("a.png"), "shot-a");
        Files.writeString(directory.resolve("b.png"), "shot-b");
        Files.writeString(directory.resolve("checklist.json"), """
                {"schema_version":1,"rows":[
                 {"scenario":"A","expected":"ea","automated":"AUTOMATED_PASS","observed_by_program":"oa",
                  "screenshots":["a.png"],"owner_status":"NOT_TESTED"},
                 {"scenario":"B","expected":"eb","automated":"AUTOMATED_PASS","observed_by_program":"ob",
                  "screenshots":["b.png"],"owner_status":"NOT_TESTED"},
                 {"scenario":"C","expected":"ec","automated":"NOT_AUTOMATED","observed_by_program":"oc",
                  "screenshots":[],"owner_status":"NOT_TESTED"}]}
                """);
        review = new GuiChecklistReview(directory);
    }

    @Test void everyRowIsInTheInboxUntilTheOwnerDecides() throws Exception {
        assertThat(review.inbox()).extracting(GuiChecklistReview.Row::scenario).containsExactly("A", "B", "C");
        assertThat(directory.resolve(GuiChecklistReview.RECEIPT_FILE)).doesNotExist();
    }

    @Test void listingNeverWritesAReceipt() throws Exception {
        review.rows();
        review.inbox();
        assertThat(directory.resolve(GuiChecklistReview.RECEIPT_FILE)).doesNotExist();
    }

    @Test void aPassDecisionRemovesTheRowAndWritesTheReceiptFormat() throws Exception {
        review.record(1, Status.PASS, "looked fine");

        assertThat(review.inbox()).extracting(GuiChecklistReview.Row::scenario).containsExactly("B", "C");
        var receipt = new ObjectMapper().readTree(directory.resolve("owner-review.json").toFile());
        assertThat(receipt.path("schema_version").asInt()).isEqualTo(1);
        var decision = receipt.path("decisions").get(0);
        assertThat(decision.path("scenario").asText()).isEqualTo("A");
        assertThat(decision.path("owner_status").asText()).isEqualTo("PASS");
        assertThat(decision.path("note").asText()).isEqualTo("looked fine");
        assertThat(decision.path("evidence_sha256").asText()).matches("[0-9a-f]{64}");
        assertThat(decision.path("decided_at").asText()).isNotBlank();
    }

    @Test void changedScreenshotReturnsTheRowToTheInbox() throws Exception {
        review.record(1, Status.PASS, "");
        Files.writeString(directory.resolve("a.png"), "different pixels");

        var row = review.inbox().get(0);
        assertThat(row.scenario()).isEqualTo("A");
        assertThat(row.state()).isEqualTo(State.EVIDENCE_CHANGED);
        assertThat(row.decision()).isPresent();
    }

    @Test void changedAutomatedResultReturnsTheRowToTheInbox() throws Exception {
        review.record(3, Status.PASS, "");
        Files.writeString(directory.resolve("checklist.json"), Files.readString(directory.resolve("checklist.json"))
                .replace("\"oc\"", "\"oc changed\""));

        assertThat(review.inbox()).extracting(GuiChecklistReview.Row::scenario).contains("C");
    }

    @Test void aReceiptWithoutAHashCountsAsCurrent() throws Exception {
        Files.writeString(directory.resolve("owner-review.json"), """
                {"schema_version":1,"decisions":[{"scenario":"B","owner_status":"PASS","note":"old"}]}
                """);

        assertThat(review.inbox()).extracting(GuiChecklistReview.Row::scenario).containsExactly("A", "C");
    }

    @Test void notTestedStaysInTheInboxAndFailNeedsANote() throws Exception {
        review.record(2, Status.NOT_TESTED, "");
        assertThat(review.inbox()).hasSize(3);

        assertThatThrownBy(() -> review.record(2, Status.FAIL, "  ")).isInstanceOf(IllegalArgumentException.class);
        review.record(2, Status.FAIL, "wrong text");
        assertThat(review.inbox()).hasSize(2);
        assertThat(review.rows().get(1).decision().orElseThrow().status()).isEqualTo(Status.FAIL);
    }

    @Test void recordingOneRowKeepsOtherDecisions() throws Exception {
        review.record(1, Status.PASS, "");
        review.record(2, Status.PASS, "");

        assertThat(review.inbox()).extracting(GuiChecklistReview.Row::scenario).containsExactly("C");
    }

    @Test void screenshotPathsMayNotLeaveTheChecklistFolder() throws Exception {
        Files.writeString(directory.resolve("checklist.json"), """
                {"rows":[{"scenario":"X","screenshots":["../secret.png"]}]}
                """);
        assertThatThrownBy(review::rows).isInstanceOf(java.io.IOException.class);
    }
}
