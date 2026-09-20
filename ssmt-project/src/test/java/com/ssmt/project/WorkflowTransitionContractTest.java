package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowTransitionContractTest {
    private final WorkflowTransitionContract transitions =
            new WorkflowTransitionContract();

    @Test
    void followsTheSharedHappyPathAndAllowsRestartedImport() throws Exception {
        LocalizationProject untranslated = project("Source", "");
        LocalizationProject translated = project("Source", "Translation");

        var accepted = transitions.inputAccepted("example.mod");
        var ready = transitions.projectReady(accepted, untranslated);
        var pending = transitions.responsePending(ready, untranslated);
        var imported = transitions.responseImported(pending, untranslated, translated);
        var published = transitions.outputPublished(imported, translated);
        var restartedImport = transitions.responseImported(ready, untranslated, translated);

        assertThat(ready.phase()).isEqualTo(WorkflowTransitionContract.Phase.PROJECT_READY);
        assertThat(pending.untranslatedCount()).isEqualTo(1);
        assertThat(published.phase()).isEqualTo(
                WorkflowTransitionContract.Phase.OUTPUT_PUBLISHED);
        assertThat(restartedImport.phase()).isEqualTo(
                WorkflowTransitionContract.Phase.RESPONSE_IMPORTED);
    }

    @Test
    void rejectsForeignSourcesEntryDriftAndIncompletePublication() throws Exception {
        LocalizationProject untranslated = project("Source", "");
        var accepted = transitions.inputAccepted("example.mod");
        var ready = transitions.projectReady(accepted, untranslated);
        LocalizationProject changedSource = project("Changed", "Translation");
        LocalizationProject foreign = new LocalizationProject(1, "foreign.mod",
                "foreign.patch", "Foreign", untranslated.entries());

        assertThatThrownBy(() -> transitions.projectReady(accepted, foreign))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("accepted source mod");
        assertThatThrownBy(() -> transitions.responseImported(
                ready, untranslated, changedSource))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("protected project entry set");
        assertThatThrownBy(() -> transitions.outputPublished(ready, untranslated))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("still need translation");
    }

    private static LocalizationProject project(String source, String translation) {
        ProjectEntry entry = new ProjectEntry(Path.of("data/strings/strings.json"),
                "/welcome", source, translation, TranslationProvenance.HUMAN_EDITED);
        return new LocalizationProject(1, "example.mod", "example.patch",
                "Example", List.of(entry));
    }
}
