package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.ai.AiProviderException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiRoundTripAutoTest {
    private static final int ROWS = 45;
    @TempDir Path temporary;

    private Path mod() throws IOException {
        Path source = temporary.resolve("mod");
        Files.createDirectories(source.resolve("data"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"ai\",\"name\":\"Ai\",\"version\":\"1\"}");
        StringBuilder csv = new StringBuilder("id,name\n");
        for (int row = 1; row <= ROWS; row++) {
            csv.append("r").append(row).append(",名称").append(row).append('\n');
        }
        Files.writeString(source.resolve("data/ships.csv"), csv.toString());
        return source;
    }

    private AutoWorkflow workflow(List<String> progress) {
        return new AutoWorkflow(temporary.resolve("internal/catalog.db"), temporary.resolve("internal/workspaces"))
                .progress(progress::add);
    }

    private static String english(String source) {
        return "Item " + source.substring(2);
    }

    @Test void nothingIsSentWithoutConfirmationAndTheCountAndProviderAreShownFirst() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var result = workflow(new ArrayList<>()).aiProvider(request -> {
            calls.incrementAndGet();
            return english(request.sourceText());
        }, "FAKE test-model at http://localhost").run(mod());
        assertThat(result.status()).isEqualTo(AutoRunResult.Status.AI_SEND_NEEDS_CONFIRMATION);
        assertThat(result.detail()).contains("FAKE test-model").contains(Integer.toString(ROWS))
                .contains("--confirm-ai-send");
        assertThat(calls).hasValue(0);
    }

    @Test void confirmedRunSendsInChunksReportsProgressAppliesRuleApprovedResultsAndBuilds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> progress = new ArrayList<>();
        Path source = mod();
        var result = workflow(progress).confirmAiSend(true).aiProvider(request -> {
            calls.incrementAndGet();
            return english(request.sourceText());
        }, "FAKE").run(source);
        assertThat(result.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(calls).hasValue(ROWS);
        assertThat(progress).hasSize(3).first().asString().startsWith("[20/45]");
        assertThat(progress.getLast()).startsWith("[45/45]");
        assertThat(result.workspace().resolve("ai-checkpoint.jsonl")).doesNotExist();
        assertThat(result.workspace().resolve("ai-review.json")).doesNotExist();
        try (var paths = Files.list(temporary)) {
            Path output = paths.filter(path -> Files.isRegularFile(path.resolve(".ssmt-build-fingerprint")))
                    .findFirst().orElseThrow();
            assertThat(Files.readString(output.resolve("data/ships.csv"))).contains("r45,Item 45");
        }
        assertThat(Files.readString(source.resolve("data/ships.csv"))).contains("名称1");
    }

    @Test void aStoppedRunResumesFromItsCheckpointInsteadOfStartingOver() throws Exception {
        Path source = mod();
        AtomicInteger first = new AtomicInteger();
        var interrupted = workflow(new ArrayList<>()).confirmAiSend(true).aiProvider(request -> {
            if (first.incrementAndGet() == 25) {
                throw new AiProviderException("network down");
            }
            return english(request.sourceText());
        }, "FAKE").run(source);
        assertThat(interrupted.status()).isEqualTo(AutoRunResult.Status.AI_ROUND_TRIP_INTERRUPTED);
        assertThat(interrupted.detail()).contains("network down").contains("resume");
        Path checkpoint = interrupted.workspace().resolve("ai-checkpoint.jsonl");
        assertThat(Files.readAllLines(checkpoint)).hasSize(1 + 24);

        AtomicInteger second = new AtomicInteger();
        var resumed = workflow(new ArrayList<>()).confirmAiSend(true).aiProvider(request -> {
            second.incrementAndGet();
            return english(request.sourceText());
        }, "FAKE").run(source);
        assertThat(resumed.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(second).hasValue(ROWS - 24);
        assertThat(checkpoint).doesNotExist();
    }

    @Test void draftsThatFailTheRulesStayUntranslatedAndAreListedForReview() throws Exception {
        Path source = mod();
        var result = workflow(new ArrayList<>()).confirmAiSend(true).aiProvider(request ->
                request.sourceText().endsWith("7") ? request.sourceText() : english(request.sourceText()), "FAKE")
                .run(source);
        assertThat(result.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        assertThat(Files.readString(result.workspace().resolve("ai-review.json"))).contains("equals the source").contains("\"approved_by_rules\" : 41");
        try (var paths = Files.list(temporary)) {
            assertThat(paths.filter(path -> String.valueOf(path.getFileName()).endsWith("Translate to English.json")))
                    .hasSize(1);
        }
    }
}
