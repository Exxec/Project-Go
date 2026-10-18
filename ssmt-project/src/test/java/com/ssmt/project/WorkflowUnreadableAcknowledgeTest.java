package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowUnreadableAcknowledgeTest {
    @TempDir Path temporary;

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"wf\",\"name\":\"Wf\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/good.csv"), "id,name\nx,中文\n");
        Files.write(mod.resolve("data/config/bad.json"), new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
        return mod;
    }

    @Test void sessionListsUnreadableInputsAndTheAcknowledgementIsDurableAndUnlocksTheBuild() throws Exception {
        Path source = mod();
        var workflow = new TranslationWorkflow(temporary.resolve("owned"));
        var session = workflow.loadMod(source);
        assertThat(session.unreadableInputs()).singleElement().asString().startsWith("data/config/bad.json: ");
        assertThat(session.unreadableAcknowledged()).isFalse();
        var entry = session.project().entries().getFirst().withTranslatedText("English");
        var translated = new TranslationWorkflow.Session(session.source(), session.workspace(),
                session.project().withEntries(List.of(entry)), session.identity(), session.presentation(),
                session.sourceLanguage(), session.revision(), session.needsReview());
        assertThat(translated.unreadableAcknowledged()).isFalse();

        var acknowledged = workflow.acknowledgeUnreadable(session);
        assertThat(acknowledged.unreadableAcknowledged()).isTrue();
        assertThat(workflow.acknowledgeUnreadable(acknowledged)).isSameAs(acknowledged);
        var resumed = new TranslationWorkflow(temporary.resolve("owned")).loadMod(source);
        assertThat(resumed.unreadableAcknowledged()).isTrue();
        assertThat(resumed.unreadableInputs()).hasSize(1);
        assertThatThrownBy(() -> workflow.acknowledgeUnreadable(session)).isInstanceOf(ProjectException.class);
    }

    @Test void installedQualityCarriesLeftoverReasons() throws Exception {
        Path output = temporary.resolve("installed");
        Files.createDirectories(output);
        Files.writeString(output.resolve("mod_info.json"), "{\"id\":\"wf\",\"name\":\"Wf\",\"version\":\"1\"}");
        Files.writeString(output.resolve("notes.md"), "中文");
        Files.writeString(output.resolve("a.csv"), "id,name\nx,中文\n");
        var quality = new InstalledCopyEvidence().inspect(output);
        assertThat(quality.reasons()).containsEntry("UNTRANSLATED", 1).containsEntry("UNSUPPORTED_TYPE", 1);
    }
}
