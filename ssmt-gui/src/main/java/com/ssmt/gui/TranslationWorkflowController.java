package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import com.ssmt.project.TranslationWorkflow;
import java.nio.file.Path;
import java.util.Optional;

/** Thin normal-workflow adapter; only successfully committed sessions become active. */
public final class TranslationWorkflowController {
    private final TranslationWorkflow workflow;
    private TranslationWorkflow.Session session;

    public TranslationWorkflowController(TranslationWorkflow workflow) { this.workflow = workflow; }

    public Optional<TranslationWorkflow.Session> session() { return Optional.ofNullable(session); }

    public void loadMod(Path source) throws ProjectException { session = workflow.loadMod(source); }

    public void exportTranslation(Path destination) throws ProjectException {
        workflow.exportTranslation(requireSession(), destination);
    }

    public void importTranslation(Path response) throws ProjectException {
        session = workflow.importTranslation(requireSession(), response);
    }

    public void buildPatch(Path destination) throws ProjectException {
        workflow.buildPatch(requireSession(), destination);
    }

    private TranslationWorkflow.Session requireSession() throws ProjectException {
        if (session == null) { throw new ProjectException("Choose a mod first."); }
        return session;
    }
}
