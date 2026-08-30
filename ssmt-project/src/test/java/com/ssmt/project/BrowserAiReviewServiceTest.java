package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserAiReviewServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    @Test
    void exportsDeterministicIndependentlyImportableBatches() throws Exception {
        LocalizationProject project = project();
        BrowserAiReviewService service = new BrowserAiReviewService();

        BrowserAiReviewExport exported = service.export(
                temporary.resolve("review"), project, "Example", "zh", "en", 1,
                "舰长 = captain");

        assertThat(exported.parts()).hasSize(2);
        assertThat(exported.manifest()).isPresent();
        assertThat(Files.readString(exported.root().resolve("PROMPT.txt")))
                .contains("Source:", "Local machine draft:", "Context:");
        ObjectNode first = (ObjectNode) JSON.readTree(
                exported.parts().getFirst().resolve("TRANSLATION_REQUEST.json").toFile());
        assertThat(first.path("entries").get(0).path("localMachineDraft").asText())
                .isEqualTo("Accepted");
        assertThat(first.path("entries").get(0).path("terminology").asText())
                .isEqualTo("舰长 = captain");
        assertThat(first.path("entries").get(0).path("translation").asText()).isEmpty();
    }

    @Test
    void importsDraftWithoutOverwritingAcceptedTranslation() throws Exception {
        LocalizationProject project = project();
        BrowserAiReviewService service = new BrowserAiReviewService();
        BrowserAiReviewExport exported = service.export(
                temporary.resolve("review"), project, "Example", "zh", "en", 10, "");
        Path response = exported.parts().getFirst().resolve("TRANSLATION_REQUEST.json");
        ObjectNode json = (ObjectNode) JSON.readTree(response.toFile());
        ((ObjectNode) json.withArray("entries").get(0)).put("translation", "AI overwrite");
        ((ObjectNode) json.withArray("entries").get(1)).put("translation", "New draft");
        JSON.writeValue(response.toFile(), json);

        AiTranslationImportResult result = service.importResponse(response, project);

        assertThat(result.project().entries().get(0).translatedText()).isEqualTo("Accepted");
        assertThat(result.project().entries().get(0).provenance())
                .isEqualTo(TranslationProvenance.HUMAN_EDITED);
        assertThat(result.project().entries().get(1).translatedText()).isEqualTo("New draft");
        assertThat(result.project().entries().get(1).provenance())
                .isEqualTo(TranslationProvenance.AI_TRANSLATED);
    }

    @Test
    void rejectsMissingBatchEntryBeforeChangingProject() throws Exception {
        LocalizationProject project = project();
        BrowserAiReviewService service = new BrowserAiReviewService();
        BrowserAiReviewExport exported = service.export(
                temporary.resolve("review"), project, "Example", "zh", "en", 10, "");
        Path response = exported.parts().getFirst().resolve("TRANSLATION_REQUEST.json");
        ObjectNode json = (ObjectNode) JSON.readTree(response.toFile());
        ((ObjectNode) json.withArray("entries").get(0)).put("translation", "Reviewed");
        json.withArray("entries").remove(1);
        JSON.writeValue(response.toFile(), json);

        assertThatThrownBy(() -> service.importResponse(response, project))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("entry count");
        assertThat(project.entries().get(1).translatedText()).isEmpty();
    }

    private static LocalizationProject project() {
        return new LocalizationProject(1, "source.mod", "translated.mod", "Example",
                List.of(
                        new ProjectEntry(Path.of("data/a.json"), "/a", "原文", "Accepted",
                                TranslationProvenance.HUMAN_EDITED),
                        new ProjectEntry(Path.of("data/b.json"), "/b", "另一段", "",
                                TranslationProvenance.MANUAL_IMPORT)));
    }
}
