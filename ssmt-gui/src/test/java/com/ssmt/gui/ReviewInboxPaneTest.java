package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.project.GuiChecklistReview.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the inbox pane on the JavaFX thread; skipped when no JavaFX toolkit can start (headless CI). */
class ReviewInboxPaneTest {
    @TempDir Path directory;

    @BeforeAll static void startToolkit() throws Exception {
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(30, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // started by an earlier test in this JVM
        } catch (Exception | UnsatisfiedLinkError noDisplay) {
            Assumptions.abort("JavaFX could not start: " + noDisplay);
        }
        Platform.setImplicitExit(false);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Exception | Error failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    @Test void loadingNeverRecordsAndOnlyAnExplicitButtonDoes() throws Exception {
        Files.writeString(directory.resolve("checklist.json"), """
                {"rows":[{"scenario":"A","expected":"e","automated":"AUTOMATED_PASS","observed_by_program":"o",
                 "screenshots":["missing.png"]},
                 {"scenario":"B","expected":"e","automated":"NOT_AUTOMATED","observed_by_program":"o","screenshots":[]}]}
                """);
        ReviewInboxPane pane = onFx(ReviewInboxPane::new);
        onFx(() -> {
            pane.setFolder(directory);
            pane.reload();
            return null;
        });
        assertThat(onFx(pane::inboxSize)).isEqualTo(2);
        assertThat(directory.resolve("owner-review.json")).doesNotExist();

        onFx(() -> {
            pane.select(0);
            pane.setNote("");
            pane.decide(Status.FAIL);
            return null;
        });
        assertThat(directory.resolve("owner-review.json")).doesNotExist();

        onFx(() -> {
            pane.decide(Status.PASS);
            return null;
        });
        assertThat(directory.resolve("owner-review.json")).isRegularFile();
        assertThat(onFx(pane::inboxSize)).isEqualTo(1);
    }
}
