package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.ProjectException;
import com.ssmt.project.WorkflowPreferences;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationWorkflowControllerTest {
    @TempDir Path directory;

    @Test void normalActionsUseDurableFacadeAndFailedImportDoesNotReplaceActiveSession() throws Exception {
        Path source = directory.resolve("source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"gui\",\"name\":\"GUI\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        var controller = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")),
                new WorkflowPreferences(directory.resolve("settings.json")));
        controller.loadMod(source);
        var previous = controller.session().orElseThrow();
        Path response = directory.resolve("response.json");
        controller.exportTranslation(response);
        Files.writeString(response, Files.readString(response).replace("\"translation\" : \"\"", "\"translation\" : \"Bonjour\""));
        try (var channel = FileChannel.open(previous.workspace().resolve(".lock"), StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertThat(lock.isValid()).isTrue();
            assertThatThrownBy(() -> controller.importTranslation(response)).isInstanceOf(ProjectException.class);
        }
        assertThat(controller.session().orElseThrow()).isSameAs(previous);
        controller.importTranslation(response);
        assertThat(controller.session().orElseThrow().project().entries().getFirst().translatedText()).isEqualTo("Bonjour");
        controller.buildPatch(directory.resolve("output"));
        assertThat(controller.modsDestination()).contains(directory.toRealPath());
        assertThat(directory.resolve("output/Project Go Changes.csv")).doesNotExist();
        assertThat(directory.resolve("output-source-backup")).doesNotExist();
        var restart = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")),
                new WorkflowPreferences(directory.resolve("settings.json")));
        assertThat(restart.modsDestination()).contains(directory.toRealPath());
        restart.loadMod(source);
        assertThat(restart.session().orElseThrow().project()).isEqualTo(controller.session().orElseThrow().project());
    }

    @Test void installedCopyNameIsReadableAndNeverLeaksAnInternalIdOrDigest() throws Exception {
        Path source = directory.resolve("Named Mod");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"a16709513_wkt\",\"name\":\"Edmund Church\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        var controller = new TranslationWorkflowController(
                new TranslationWorkflow(directory.resolve("named-owned")),
                new WorkflowPreferences(directory.resolve("named-settings.json")));

        controller.loadMod(source);

        assertThat(controller.installedFolderName()).isEqualTo("Edmund Church - English");
        assertThat(controller.aiRequestFilename())
                .isEqualTo("Edmund Church - Translate to English.json");
        Path mods = Files.createDirectories(directory.resolve("mods"));
        assertThat(controller.outputBelow(mods)).isEqualTo(mods.resolve("Edmund Church - English"));
        // The generated id stays internal: only the readable mod name is presented.
        assertThat(controller.installedFolderName()).doesNotContain("a16709513_wkt");
        assertThat(controller.outputBelow(mods).getFileName().toString())
                .doesNotContain("a16709513_wkt");
    }

    @Test void unifiedDropRoutesArchivesAndResponsesAndRejectsUnknownFiles() throws Exception {
        Path archive = directory.resolve("example.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("wrapped/mod_info.json"));
            output.write("{\"id\":\"drop\",\"name\":\"Dropped\"}".getBytes());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("wrapped/data/strings/strings.json"));
            output.write("{\"hello\":\"Hello\"}".getBytes());
            output.closeEntry();
        }
        var controller = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")),
                new WorkflowPreferences(directory.resolve("settings.json")));

        assertThat(controller.acceptDrop(archive))
                .isEqualTo(TranslationWorkflowController.DropResult.MOD_LOADED);
        var active = controller.session().orElseThrow();
        Path attemptedOutput = directory.resolve("attempted-output");
        assertThatThrownBy(() -> controller.buildPatch(attemptedOutput))
                .isInstanceOf(ProjectException.class);
        Path canonicalOutput = directory.toRealPath().resolve("attempted-output");
        assertThat(controller.lastOutput()).contains(canonicalOutput);
        var afterCrash = new TranslationWorkflowController(
                new TranslationWorkflow(directory.resolve("restart-owned")),
                new WorkflowPreferences(directory.resolve("settings.json")));
        assertThat(afterCrash.recoveryOutput()).contains(canonicalOutput);
        Path response = directory.resolve("anything.json");
        controller.exportTranslation(response);
        Files.writeString(response, Files.readString(response)
                .replace("\"translation\" : \"\"", "\"translation\" : \"Bonjour\""));
        assertThat(controller.acceptDrop(response))
                .isEqualTo(TranslationWorkflowController.DropResult.RESPONSE_IMPORTED);
        assertThat(controller.session().orElseThrow().project().entries().getFirst().translatedText())
                .isEqualTo("Bonjour");

        Path unknown = Files.writeString(directory.resolve("notes.txt"), "not a mod");
        assertThatThrownBy(() -> controller.acceptDrop(unknown)).isInstanceOf(ProjectException.class);
        assertThat(controller.session().orElseThrow().source()).isEqualTo(active.source());
        controller.reset();
        assertThat(controller.session()).isEmpty();
        assertThat(controller.lastOutput()).isEmpty();
    }

    @Test void legacyWorkNeedsAnExplicitChoiceAndAdoptionPreservesItsFile() throws Exception {
        Path source = directory.resolve("legacy-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"legacy-gui\",\"name\":\"Legacy GUI\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        var service = new com.ssmt.project.LocalizationProjectService();
        var old = service.create(source, "legacy-gui.translation", "Legacy English");
        old = old.withEntries(old.entries().stream()
                .map(entry -> entry.withTranslatedText("Bonjour")).toList());
        Path oldDirectory = Files.createDirectories(directory.resolve("Project Go - Legacy GUI"));
        Path oldFile = oldDirectory.resolve("legacy.ssmt.json");
        service.write(oldFile, old);
        byte[] before = Files.readAllBytes(oldFile);
        var controller = new TranslationWorkflowController(
                new TranslationWorkflow(directory.resolve("legacy-owned")),
                new WorkflowPreferences(directory.resolve("legacy-settings.json")));

        org.assertj.core.api.ThrowableAssert.ThrowingCallable opening = () ->
                controller.loadInput(source);
        assertThatThrownBy(opening).isInstanceOf(LegacyProjectsFoundException.class);
        assertThat(controller.session()).isEmpty();
        LegacyProjectsFoundException choice = org.assertj.core.api.Assertions
                .catchThrowableOfType(LegacyProjectsFoundException.class, opening);
        controller.adoptLegacy(choice.input(), choice.candidates().getFirst());
        assertThat(controller.session().orElseThrow().project().entries().getFirst()
                .translatedText()).isEqualTo("Bonjour");
        assertThat(Files.readAllBytes(oldFile)).containsExactly(before);
    }

    @Test void responseNamedModInfoIsValidatedAsAResponseWhenWorkIsActive() throws Exception {
        Path source = directory.resolve("source-named-response");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"named\",\"name\":\"Named\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        var controller = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")),
                new WorkflowPreferences(directory.resolve("settings.json")));
        controller.loadInput(source);
        Path returned = Files.createDirectories(directory.resolve("returned"));
        Path response = returned.resolve("mod_info.json");
        controller.exportTranslation(response);
        Files.writeString(response, Files.readString(response)
                .replace("\"translation\" : \"\"", "\"translation\" : \"Bonjour\""));

        assertThat(controller.acceptDrop(response))
                .isEqualTo(TranslationWorkflowController.DropResult.RESPONSE_IMPORTED);
        assertThat(controller.session().orElseThrow().project().entries().getFirst().translatedText())
                .isEqualTo("Bonjour");
    }
}
