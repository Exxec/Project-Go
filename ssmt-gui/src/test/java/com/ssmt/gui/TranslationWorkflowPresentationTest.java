package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TranslationWorkflowPresentationTest {
    @Test
    void successfulActionsAdvanceAcrossThreeStages() {
        var presentation = new TranslationWorkflowPresentation();

        assertThat(presentation.action()).isEqualTo(TranslationWorkflowPresentation.Action.CHOOSE_MOD);
        assertThat(presentation.action().stage()).isEqualTo(1);

        presentation.completed(TranslationWorkflowPresentation.Action.CHOOSE_MOD, false);
        assertThat(presentation.action()).isEqualTo(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION);
        assertThat(presentation.action().stage()).isEqualTo(2);

        presentation.completed(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION, false);
        assertThat(presentation.action()).isEqualTo(TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION);
        assertThat(presentation.action().stage()).isEqualTo(2);

        presentation.completed(TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION, true);
        assertThat(presentation.action()).isEqualTo(TranslationWorkflowPresentation.Action.BUILD_COPY);
        assertThat(presentation.action().stage()).isEqualTo(3);
    }

    @Test
    void labelsDescribeOneImmediateActionWithoutNumberingFourButtons() {
        var presentation = new TranslationWorkflowPresentation();

        assertThat(presentation.action().buttonText()).isEqualTo("Choose a Mod");
        presentation.completed(TranslationWorkflowPresentation.Action.CHOOSE_MOD, false);
        assertThat(presentation.action().buttonText()).isEqualTo("Save AI Translation Request");
        presentation.completed(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION, false);
        assertThat(presentation.action().buttonText()).isEqualTo("Open AI Translation Response");
        presentation.completed(TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION, true);
        assertThat(presentation.action().buttonText()).isEqualTo("Create Translated Mod");
        presentation.completed(TranslationWorkflowPresentation.Action.BUILD_COPY, true);
        assertThat(presentation.action().buttonText()).isEqualTo("Translate Another Mod");
    }

    @Test
    void completedWorkSkipsExchangeAndPartialImportsReturnToExport() {
        var presentation = new TranslationWorkflowPresentation();

        presentation.completed(TranslationWorkflowPresentation.Action.CHOOSE_MOD, true);
        assertThat(presentation.action())
                .isEqualTo(TranslationWorkflowPresentation.Action.BUILD_COPY);

        presentation = new TranslationWorkflowPresentation();
        presentation.completed(TranslationWorkflowPresentation.Action.CHOOSE_MOD, false);
        presentation.completed(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION, false);
        presentation.completed(TranslationWorkflowPresentation.Action.IMPORT_TRANSLATION, false);
        assertThat(presentation.action())
                .isEqualTo(TranslationWorkflowPresentation.Action.EXPORT_TRANSLATION);
    }

    @Test
    void completedInstallHasAResultStateAndResetReturnsToChoose() {
        var presentation = new TranslationWorkflowPresentation();
        presentation.completed(TranslationWorkflowPresentation.Action.CHOOSE_MOD, true);
        presentation.completed(TranslationWorkflowPresentation.Action.BUILD_COPY, true);

        assertThat(presentation.action()).isEqualTo(TranslationWorkflowPresentation.Action.COMPLETE);
        presentation.reset();
        assertThat(presentation.action()).isEqualTo(TranslationWorkflowPresentation.Action.CHOOSE_MOD);
    }
}
