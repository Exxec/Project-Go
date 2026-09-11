package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.WorkflowPreferences;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;

/** Thin normal-workflow adapter; only successfully committed sessions become active. */
public final class TranslationWorkflowController {
    private final TranslationWorkflow workflow;
    private final WorkflowPreferences preferences;
    private TranslationWorkflow.Session session;
    private Path lastOutput;
    private String notice = "";

    /** Result of routing one item through the unified drop surface. */
    public enum DropResult { MOD_LOADED, RESPONSE_IMPORTED }

    public TranslationWorkflowController(TranslationWorkflow workflow) {
        this(workflow, new WorkflowPreferences());
    }

    TranslationWorkflowController(TranslationWorkflow workflow, WorkflowPreferences preferences) {
        this.workflow = workflow;
        this.preferences = preferences;
    }

    public Optional<TranslationWorkflow.Session> session() { return Optional.ofNullable(session); }

    public Optional<Path> modsDestination() { return preferences.modsDestination(); }

    public Optional<Path> lastOutput() { return Optional.ofNullable(lastOutput); }

    public Optional<String> notice() {
        return notice.isBlank() ? Optional.empty() : Optional.of(notice);
    }

    public void loadMod(Path source) throws ProjectException {
        TranslationWorkflow.Session candidate = workflow.loadMod(source);
        session = candidate;
        lastOutput = null;
    }

    public void loadInput(Path input) throws ProjectException {
        TranslationWorkflow.Session candidate = workflow.loadInput(input);
        session = candidate;
        lastOutput = null;
    }

    /** Routes a folder, ZIP, mod_info.json, or returned translation JSON. */
    public DropResult acceptDrop(Path input) throws ProjectException {
        Path supplied = input.toAbsolutePath().normalize();
        Path fileName = supplied.getFileName();
        String name = fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
        if (Files.isDirectory(supplied) || name.endsWith(".zip")) {
            loadInput(supplied);
            return DropResult.MOD_LOADED;
        }
        if (Files.isRegularFile(supplied) && name.endsWith(".json")) {
            if (session != null) {
                try {
                    importTranslation(supplied);
                    return DropResult.RESPONSE_IMPORTED;
                } catch (ProjectException importFailure) {
                    if (!name.equals("mod_info.json")) {
                        throw importFailure;
                    }
                }
            }
            if (name.equals("mod_info.json")) {
                loadInput(supplied);
                return DropResult.MOD_LOADED;
            }
            importTranslation(supplied);
            return DropResult.RESPONSE_IMPORTED;
        }
        throw new ProjectException("Drop a mod ZIP, mod folder, mod_info.json, or returned AI JSON.");
    }

    public void exportTranslation(Path destination) throws ProjectException {
        workflow.exportTranslation(requireSession(), destination);
    }

    public void importTranslation(Path response) throws ProjectException {
        session = workflow.importTranslation(requireSession(), response);
    }

    public void buildPatch(Path destination) throws ProjectException {
        notice = "";
        lastOutput = destination.toAbsolutePath().normalize();
        workflow.buildPatch(requireSession(), lastOutput);
        Path parent = lastOutput.getParent();
        if (parent != null) {
            try {
                preferences.rememberModsDestination(parent);
            } catch (ProjectException exception) {
                notice = exception.getMessage();
            }
        }
    }

    public Path outputBelow(Path destination) throws ProjectException {
        TranslationWorkflow.Session active = requireSession();
        String id = active.project().sourceModId().replaceAll("[^A-Za-z0-9._-]", "_");
        return destination.toAbsolutePath().normalize().resolve(id + "-translated");
    }

    public void reset() {
        session = null;
        lastOutput = null;
        notice = "";
    }

    private TranslationWorkflow.Session requireSession() throws ProjectException {
        if (session == null) { throw new ProjectException("Choose a mod first."); }
        return session;
    }
}
