package com.ssmt.gui;

/** State for the normal three-stage translation flow. */
final class TranslationWorkflowPresentation {
    enum Action {
        CHOOSE_MOD(1, "normal.primary.choose", "normal.prompt.choose"),
        EXPORT_TRANSLATION(2, "normal.primary.export", "normal.prompt.export"),
        IMPORT_TRANSLATION(2, "normal.primary.import", "normal.prompt.import"),
        BUILD_COPY(3, "normal.primary.build", "normal.prompt.build");

        private final int stage;
        private final String buttonKey;
        private final String promptKey;

        Action(int stage, String buttonKey, String promptKey) {
            this.stage = stage;
            this.buttonKey = buttonKey;
            this.promptKey = promptKey;
        }

        int stage() { return stage; }

        String buttonText() { return GuiText.get(buttonKey); }

        String prompt() { return GuiText.get(promptKey); }
    }

    private Action action = Action.CHOOSE_MOD;

    Action action() { return action; }

    void completed(Action completed, boolean readyToBuild) {
        action = switch (completed) {
            case CHOOSE_MOD, IMPORT_TRANSLATION -> readyToBuild
                    ? Action.BUILD_COPY
                    : Action.EXPORT_TRANSLATION;
            case EXPORT_TRANSLATION -> Action.IMPORT_TRANSLATION;
            case BUILD_COPY -> Action.BUILD_COPY;
        };
    }
}
