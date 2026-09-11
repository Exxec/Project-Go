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
        assertThat(controller.lastOutput()).contains(attemptedOutput.toAbsolutePath().normalize());
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
