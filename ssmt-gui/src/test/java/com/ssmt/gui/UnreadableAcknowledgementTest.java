package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.WorkflowPreferences;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnreadableAcknowledgementTest {
    @TempDir Path directory;

    @Test void buildWaitsForTheUnreadableListToBeAcceptedAndThenInstallsTheReadableText() throws Exception {
        Path source = directory.resolve("source");
        Files.createDirectories(source.resolve("data/config"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"ack\",\"name\":\"Ack\",\"version\":\"1\"}");
        Files.writeString(source.resolve("data/good.csv"), "id,name\nx,中文\n");
        Files.write(source.resolve("data/config/bad.json"),
                new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
        var controller = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("owned")),
                new WorkflowPreferences(directory.resolve("settings.json")));
        controller.loadMod(source);
        Path response = directory.resolve("response.json");
        controller.exportTranslation(response);
        Files.writeString(response, Files.readString(response).replace("\"translation\" : \"\"", "\"translation\" : \"English\""));
        controller.importTranslation(response);
        assertThat(controller.unreadableInputs()).hasSize(1);
        assertThat(controller.readyToBuild()).isFalse();
        controller.acknowledgeUnreadable();
        assertThat(controller.unreadableAcknowledged()).isTrue();
        assertThat(controller.readyToBuild()).isTrue();
        controller.buildPatch(directory.resolve("output"));
        assertThat(Files.readString(directory.resolve("output/data/good.csv"))).contains("English");
        assertThat(controller.quality().orElseThrow().unreadable()).hasSize(1);
        assertThat(controller.quality().orElseThrow().reasons()).containsEntry("UNREADABLE", 1);
    }

    @Test void glossaryProposalsCanBeApprovedThroughTheControllerWithoutLosingReadiness() throws Exception {
        Path source = directory.resolve("gsource");
        Files.createDirectories(source.resolve("data"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"gloss\",\"name\":\"Gloss\",\"version\":\"1\"}");
        Files.writeString(source.resolve("data/a.csv"), "id,name\na,维修\nb,维修\nc,维修\n");
        var controller = new TranslationWorkflowController(new TranslationWorkflow(directory.resolve("gowned")),
                new WorkflowPreferences(directory.resolve("gsettings.json")));
        controller.loadMod(source);
        assertThat(controller.glossary().proposed()).hasSize(1);
        controller.approveGlossaryTerm("维修", "Repairs", false);
        assertThat(controller.glossary().proposed()).isEmpty();
        assertThat(controller.glossary().approvedMap()).containsEntry("维修", "Repairs");
        assertThat(controller.session().orElseThrow().project().methodDocument().path("glossary")
                .path("维修").asText()).isEqualTo("Repairs");
    }
}
