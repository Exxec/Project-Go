package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One mod id must never silently merge two genuinely different sources. Project Go
 * stops and asks, and the question names mods only: no digests, paths, or internal
 * workspace names.
 */
class SourceLineageChoiceTest {
    private static final Pattern DIGEST_RUN = Pattern.compile("[0-9a-f]{16,}");
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    private Path mod(String folder, String id, String name, String strings) throws Exception {
        Path mod = directory.resolve(folder);
        Files.createDirectories(mod.resolve("data/strings"));
        Files.writeString(mod.resolve("mod_info.json"),
                "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"version\":\"1\"}",
                StandardCharsets.UTF_8);
        Files.writeString(mod.resolve("data/strings/strings.json"), strings, StandardCharsets.UTF_8);
        return mod;
    }

    private TranslationWorkflow.Session translated(TranslationWorkflow workflow, Path mod)
            throws Exception {
        TranslationWorkflow.Session session = workflow.loadMod(mod);
        Path request = directory.resolve(session.presentation().aiRequestFilename());
        workflow.exportTranslation(session, request);
        ObjectNode response = (ObjectNode) json.readTree(request.toFile());
        for (var entry : response.withArray("entries")) {
            ((ObjectNode) entry).put("translation", "Translated " + entry.path("source").asText());
        }
        json.writeValue(request.toFile(), response);
        return workflow.importTranslation(session, request);
    }

    @Test void aRadicallyDifferentSourceWithTheSameIdStopsForAnExplicitChoice() throws Exception {
        Path first = mod("First Mod", "same.id", "First Mod", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        Path second = mod("Second Mod", "same.id", "Second Mod", "{\"zzz\":\"Unrelated text\"}");
        Path savedFile = saved.workspace().resolve("project.ssmt.json");
        byte[] before = Files.readAllBytes(savedFile);

        var conflict = catchThrowableOfType(SourceIdentityConflictException.class,
                () -> workflow.loadMod(second));

        assertThat(conflict).isNotNull();
        assertThat(conflict.previousModName()).isEqualTo("First Mod");
        assertThat(conflict.currentModName()).isEqualTo("Second Mod");
        assertThat(conflict.previousEntries()).isEqualTo(1);
        assertThat(conflict.currentEntries()).isEqualTo(1);
        assertThat(conflict.source()).isEqualTo(second.toRealPath());
        // The question is about mods, never about internal keys.
        assertThat(DIGEST_RUN.matcher(conflict.getMessage()).find()).isFalse();
        assertThat(conflict.getMessage()).doesNotContain(second.toString());
        assertThat(conflict.getMessage()).doesNotContain("project.ssmt.json");
        // Nothing was merged or committed while the choice is open.
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(before);
    }

    @Test void usePreviousKeepsOneWorkspaceAndGrowsTheSavedWork() throws Exception {
        Path first = mod("First Mod", "same.id", "First Mod", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        Path second = mod("Second Mod", "same.id", "Second Mod", "{\"zzz\":\"Unrelated text\"}");

        var merged = workflow.loadMod(second, TranslationWorkflow.LineageChoice.USE_PREVIOUS);

        assertThat(merged.workspace()).isEqualTo(saved.workspace());
        assertThat(merged.project().entries()).hasSize(1);
        assertThat(merged.project().entries().getFirst().translatedText()).isBlank();
        assertThat(merged.identity().originalName()).isEqualTo("Second Mod");
    }

    @Test void startSeparatelyCreatesItsOwnWorkspaceAndResumesWithoutAskingAgain() throws Exception {
        Path first = mod("First Mod", "same.id", "First Mod", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        Path second = mod("Second Mod", "same.id", "Second Mod", "{\"zzz\":\"Unrelated text\"}");
        Path savedFile = saved.workspace().resolve("project.ssmt.json");
        byte[] before = Files.readAllBytes(savedFile);

        var separate = workflow.loadMod(second, TranslationWorkflow.LineageChoice.START_SEPARATELY);

        assertThat(separate.workspace()).isNotEqualTo(saved.workspace());
        assertThat(separate.project().entries()).hasSize(1);
        assertThat(separate.project().entries().getFirst().translatedText()).isBlank();
        // The previous work is untouched and still resumes on its own.
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(before);
        var previousAgain = new TranslationWorkflow(directory.resolve("owned")).loadMod(first);
        assertThat(previousAgain.workspace()).isEqualTo(saved.workspace());
        assertThat(previousAgain.project().entries()).allMatch(e -> !e.translatedText().isBlank());
        // The separated copy resumes without being asked again.
        var resumed = new TranslationWorkflow(directory.resolve("owned")).loadMod(second);
        assertThat(resumed.workspace()).isEqualTo(separate.workspace());
    }

    @Test void aRenamedSeparatedCopyIsStillResolvedToItsOwnWorkspace() throws Exception {
        Path first = mod("First Mod", "same.id", "First Mod", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        translated(workflow, first);
        Path second = mod("Second Mod", "same.id", "Second Mod", "{\"zzz\":\"Unrelated text\"}");
        var separate = workflow.loadMod(second, TranslationWorkflow.LineageChoice.START_SEPARATELY);
        Path moved = Files.move(second, directory.resolve("Second Mod Renamed"));

        var resumed = new TranslationWorkflow(directory.resolve("owned")).loadMod(moved);

        assertThat(resumed.workspace()).isEqualTo(separate.workspace());
        assertThat(resumed.identity().originalFolderName()).isEqualTo("Second Mod Renamed");
        // After the rename is recorded, the copy resolves directly again.
        assertThat(new TranslationWorkflow(directory.resolve("owned")).loadMod(moved).workspace())
                .isEqualTo(separate.workspace());
    }

    @Test void anOverlappingUpdateIsANormalRefreshAndNeverNeedsTheChoice() throws Exception {
        Path first = mod("First Mod", "same.id", "First Mod", "{\"a\":\"Hello\",\"b\":\"Goodbye\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        Files.writeString(first.resolve("data/strings/strings.json"),
                "{\"a\":\"Hello\",\"b\":\"Goodbye\",\"c\":\"Brand new\"}", StandardCharsets.UTF_8);

        var grown = workflow.loadMod(first);

        assertThat(grown.workspace()).isEqualTo(saved.workspace());
        assertThat(grown.project().entries()).hasSize(3);
        assertThat(grown.project().entries().stream().filter(e -> !e.translatedText().isBlank()))
                .hasSize(2);
    }

    @Test void aModWhoseTextsAllDisappearedIsNotMistakenForAFork() throws Exception {
        Path first = mod("First Mod", "same.id", "First Mod", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        Files.writeString(first.resolve("data/strings/strings.json"), "{}", StandardCharsets.UTF_8);

        var emptied = workflow.loadMod(first);

        assertThat(emptied.workspace()).isEqualTo(saved.workspace());
        assertThat(emptied.project().entries()).isEmpty();
    }

    @Test void identicalKeysDoNotMakeUnrelatedSourceTextsTheSameLineage() throws Exception {
        Path first = mod("First", "same.id", "First", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        byte[] before = Files.readAllBytes(saved.workspace().resolve("project.ssmt.json"));
        Path second = mod("Second", "same.id", "Second", "{\"a\":\"Entirely unrelated\"}");

        assertThatThrownBy(() -> workflow.loadMod(second))
                .isInstanceOf(SourceIdentityConflictException.class);
        assertThat(Files.readAllBytes(saved.workspace().resolve("project.ssmt.json"))).isEqualTo(before);
    }

    @Test void twoForksWithIdenticalKeysButDifferentTextKeepSeparateSavedWork() throws Exception {
        Path first = mod("First", "same.id", "First", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        translated(workflow, first);
        Path second = mod("Second", "same.id", "Second", "{\"z\":\"Second text\"}");
        var fork = workflow.loadMod(second, TranslationWorkflow.LineageChoice.START_SEPARATELY);
        byte[] before = Files.readAllBytes(fork.workspace().resolve("project.ssmt.json"));
        Path third = mod("Third", "same.id", "Third", "{\"z\":\"Third text\"}");

        assertThatThrownBy(() -> workflow.loadMod(third))
                .isInstanceOf(SourceIdentityConflictException.class);
        var thirdFork = workflow.loadMod(third, TranslationWorkflow.LineageChoice.START_SEPARATELY);
        assertThat(thirdFork.workspace()).isNotEqualTo(fork.workspace());
        assertThat(Files.readAllBytes(fork.workspace().resolve("project.ssmt.json"))).isEqualTo(before);
    }

    @Test void changingAllTextAtTheSameSourcePathIsStillANormalRefresh() throws Exception {
        Path first = mod("First", "same.id", "First", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var saved = translated(workflow, first);
        Files.writeString(first.resolve("data/strings/strings.json"), "{\"a\":\"Updated\"}");

        var refreshed = workflow.loadMod(first);

        assertThat(refreshed.workspace()).isEqualTo(saved.workspace());
        assertThat(refreshed.project().entries()).allMatch(entry -> entry.translatedText().isBlank());
    }

    @Test void malformedAdvisoryForksCannotFailAfterSavingAProject() throws Exception {
        Path first = mod("First", "same.id", "First", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var primary = translated(workflow, first);
        Path second = mod("Second", "same.id", "Second", "{\"z\":\"Second text\"}");
        var fork = workflow.loadMod(second, TranslationWorkflow.LineageChoice.START_SEPARATELY);
        Files.writeString(primary.workspace().resolve("lineage.json"),
                "{\"schemaVersion\":1,\"sourceModId\":\"same.id\",\"forks\":{}}");

        var resumed = workflow.loadMod(second, TranslationWorkflow.LineageChoice.START_SEPARATELY);

        assertThat(resumed.workspace()).isEqualTo(fork.workspace());
        assertThat(workflow.loadMod(first).workspace()).isEqualTo(primary.workspace());
    }

    @Test void forkLoadsRespectTheSharedPrimaryWorkspaceLock() throws Exception {
        Path first = mod("First", "same.id", "First", "{\"a\":\"Hello\"}");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var primary = translated(workflow, first);
        Path second = mod("Second", "same.id", "Second", "{\"z\":\"Second text\"}");
        workflow.loadMod(second, TranslationWorkflow.LineageChoice.START_SEPARATELY);
        try (var channel = FileChannel.open(primary.workspace().resolve(".lock"), StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertThat(lock.isValid()).isTrue();
            assertThatThrownBy(() -> workflow.loadMod(second)).isInstanceOf(ProjectException.class);
        }
    }
}
