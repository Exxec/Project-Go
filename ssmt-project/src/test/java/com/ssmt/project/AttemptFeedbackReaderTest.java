package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttemptFeedbackReaderTest {
    private static final String HASH = "a".repeat(64);
    @TempDir Path root;

    @Test void requiresClassifiedSurprisesWithEvidenceFixtureAndChange() throws Exception {
        var references = List.of(reference("evidence.log", "observed failure"),
                reference("fixture.json", "small reproduction"),
                reference("change.patch", "detector fix"));
        var surprise = new AttemptFeedbackReader.Surprise("SURPRISE-1",
                AttemptFeedbackReader.Category.DETECTOR_GAP, "Missing detector",
                "evidence.log", "fixture.json", "change.patch");
        Path record = write(new AttemptFeedbackReader.Record(
                1, HASH, "attempt-1", true, List.of(surprise), references));

        assertThat(new AttemptFeedbackReader().read(record, HASH).status())
                .isEqualTo("READY_FOR_NEXT_CANDIDATE");
    }

    @Test void pendingReviewCannotBecomeReadyAndTamperingFails() throws Exception {
        var references = List.of(reference("evidence.log", "observed failure"),
                reference("fixture.json", "small reproduction"),
                reference("change.patch", "detector fix"));
        var surprise = new AttemptFeedbackReader.Surprise("SURPRISE-1",
                AttemptFeedbackReader.Category.WORKFLOW_GAP, "Interrupted workflow",
                "evidence.log", "fixture.json", "change.patch");
        Path record = write(new AttemptFeedbackReader.Record(
                1, HASH, "attempt-1", false, List.of(surprise), references));
        assertThat(new AttemptFeedbackReader().read(record, HASH).status()).isEqualTo("REVIEW_PENDING");

        Files.writeString(root.resolve("fixture.json"), "tampered");
        assertThatThrownBy(() -> new AttemptFeedbackReader().read(record, HASH))
                .hasMessageContaining("hash mismatch");
    }

    private AttemptFeedbackReader.Reference reference(String name, String contents) throws Exception {
        Path file = Files.writeString(root.resolve(name), contents);
        return new AttemptFeedbackReader.Reference(name,
                com.ssmt.scanner.InventoryFingerprint.archive(file));
    }

    private Path write(AttemptFeedbackReader.Record record) throws Exception {
        Path file = root.resolve("feedback.json");
        new ObjectMapper().writeValue(file.toFile(), record);
        return file;
    }
}
