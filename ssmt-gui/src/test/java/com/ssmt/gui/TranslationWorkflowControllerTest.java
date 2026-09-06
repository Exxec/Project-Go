package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.ProjectException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationWorkflowControllerTest {
    @TempDir Path directory;

    @Test void fourActionsUseDurableFacadeAndFailedImportDoesNotReplaceActiveSession() throws Exception {
        Path source = directory.resolve("source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"gui\",\"name\":\"GUI\"}");
        Files.writeString(source.resolve("data/strings/strings.json"), "{\"hello\":\"Hello\"}");
        var controller = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")));
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
        assertThat(directory.resolve("output/Project Go Changes.csv")).isRegularFile();
        var restart = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")));
        restart.loadMod(source);
        assertThat(restart.session().orElseThrow().project()).isEqualTo(controller.session().orElseThrow().project());
    }
}
