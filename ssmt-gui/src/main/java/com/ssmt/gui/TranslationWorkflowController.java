package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.WorkflowPreferences;
import java.nio.file.Path;
import java.util.Optional;

/** Thin normal-workflow adapter; only successfully committed sessions become active. */
public final class TranslationWorkflowController {
    private final TranslationWorkflow workflow;
    private final WorkflowPreferences preferences;
    private TranslationWorkflow.Session session;
    private String notice = "";

    public TranslationWorkflowController(TranslationWorkflow workflow) {
        this(workflow, new WorkflowPreferences());
    }

    TranslationWorkflowController(TranslationWorkflow workflow, WorkflowPreferences preferences) {
        this.workflow = workflow;
        this.preferences = preferences;
    }

    public Optional<TranslationWorkflow.Session> session() { return Optional.ofNullable(session); }

    public Optional<Path> modsDestination() { return preferences.modsDestination(); }

    public Optional<String> notice() {
        return notice.isBlank() ? Optional.empty() : Optional.of(notice);
    }

    public void loadMod(Path source) throws ProjectException { session = workflow.loadMod(source); }

    public void exportTranslation(Path destination) throws ProjectException {
        workflow.exportTranslation(requireSession(), destination);
    }

    public void importTranslation(Path response) throws ProjectException {
        session = workflow.importTranslation(requireSession(), response);
    }

    public void buildPatch(Path destination) throws ProjectException {
        notice = "";
        workflow.buildPatch(requireSession(), destination);
        Path parent = destination.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            try {
                preferences.rememberModsDestination(parent);
            } catch (ProjectException exception) {
                notice = exception.getMessage();
            }
        }
    }

    private TranslationWorkflow.Session requireSession() throws ProjectException {
        if (session == null) { throw new ProjectException("Choose a mod first."); }
        return session;
    }
}
