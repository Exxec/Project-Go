package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnreadableInputProjectTest {
    @TempDir Path temporary;

    /** A readable CSV with Chinese text plus a JSON file whose bytes are neither UTF-8 nor decodable as JSON text. */
    private Path modWithOneBrokenFile() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"broken\",\"name\":\"Broken\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/good.csv"), "id,name\nx,中文\n");
        Files.write(mod.resolve("data/config/bad.json"), new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xb5,
            (byte) 0xe7, (byte) 0xff, '"', '}'});
        return mod;
    }

    @Test void aBrokenFileDoesNotStopProjectCreationAndIsListedWithItsError() throws Exception {
        Path mod = modWithOneBrokenFile();
        var service = new LocalizationProjectService();
        var project = service.createUsingBridgeForge(mod, "patch", "Patch");
        assertThat(project.entries()).hasSize(1);
        var unreadable = project.methodDocument().path("unreadable");
        assertThat(unreadable).hasSize(1);
        assertThat(unreadable.get(0).asText()).startsWith("data/config/bad.json: ");
    }

    @Test void buildRefusesUntilTheUnreadableListIsAcknowledgedThenPublishesTheReadableText() throws Exception {
        Path mod = modWithOneBrokenFile();
        var service = new LocalizationProjectService();
        var project = service.createUsingBridgeForge(mod, "patch", "Patch");
        var translated = project.withEntries(List.of(project.entries().getFirst().withTranslatedText("English")));
        Path output = temporary.resolve("out");
        assertThatThrownBy(() -> service.build(mod, output, translated))
                .isInstanceOf(ProjectException.class).hasMessageContaining("not acknowledged");
        assertThat(output).doesNotExist();
        var acknowledged = service.acknowledgeUnreadable(translated);
        service.build(mod, output, acknowledged);
        assertThat(Files.readString(output.resolve("data/good.csv"))).contains("English");
        assertThat(Files.readAllBytes(output.resolve("data/config/bad.json")))
                .isEqualTo(Files.readAllBytes(mod.resolve("data/config/bad.json")));
        var upFront = service.createUsingBridgeForge(mod, "patch", "Patch", true);
        assertThat(upFront.methodDocument().path(LocalizationProjectService.UNREADABLE_ACKNOWLEDGED).asBoolean())
                .isTrue();
    }

    @Test void aModWithOnlyUnreadableTextStillNeedsAcknowledgement() throws Exception {
        Path mod = modWithOneBrokenFile();
        Files.delete(mod.resolve("data/good.csv"));
        var service = new LocalizationProjectService();
        assertThatThrownBy(() -> service.createUsingBridgeForge(mod, "patch", "Patch"))
                .isInstanceOf(ProjectException.class).hasMessageContaining("acknowledge");
        assertThat(service.createUsingBridgeForge(mod, "patch", "Patch", true).entries()).isEmpty();
    }
}
