package com.ssmt.gui;

import com.ssmt.project.ProjectException;
import com.ssmt.project.LegacyProjectCandidate;
import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.WorkflowPreferences;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;
import java.util.List;

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

    public Optional<Path> recoveryOutput() {
        return Optional.ofNullable(lastOutput).or(preferences::attemptedOutput);
    }

    public Optional<String> notice() {
        return notice.isBlank() ? Optional.empty() : Optional.of(notice);
    }

    public void loadMod(Path source) throws ProjectException {
        TranslationWorkflow.Session candidate = workflow.loadMod(source);
        session = candidate;
        lastOutput = null;
        notice = "";
    }

    public void loadInput(Path input) throws ProjectException {
        List<LegacyProjectCandidate> candidates = workflow.legacyProjects(input);
        if (!candidates.isEmpty()) {
            throw new LegacyProjectsFoundException(input, candidates);
        }
        startFresh(input);
    }

    public void startFresh(Path input) throws ProjectException {
        TranslationWorkflow.Session candidate = workflow.loadInput(input);
        session = candidate;
        lastOutput = null;
        notice = "";
    }

    /**
     * Applies the user's explicit decision when one mod id maps to two genuinely
     * different sources. Project Go never makes that decision by itself.
     */
    public void resolveLineage(Path input, TranslationWorkflow.LineageChoice choice)
            throws ProjectException {
        TranslationWorkflow.Session candidate = workflow.loadInput(input, choice);
        session = candidate;
        lastOutput = null;
        notice = "";
    }

    public void adoptLegacy(Path input, LegacyProjectCandidate selected)
            throws ProjectException {
        TranslationWorkflow.Session candidate = workflow.adoptLegacy(input, selected);
        session = candidate;
        lastOutput = null;
        notice = "Old translation work was copied into Project Go; the original was unchanged.";
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
        preferences.rememberAttemptedOutput(lastOutput);
        workflow.buildPatch(requireSession(), lastOutput);
        Path parent = lastOutput.getParent();
        if (parent != null) {
            try {
                preferences.rememberSuccessfulPublication(parent, lastOutput);
            } catch (ProjectException exception) {
                notice = exception.getMessage();
            }
        }
    }

    public void recoveryCompleted(Path recoveredOutput) throws ProjectException {
        preferences.clearAttemptedOutput(recoveredOutput);
    }

    /** Returns the readable installed-copy folder below a chosen mods directory. */
    public Path outputBelow(Path destination) throws ProjectException {
        return destination.toAbsolutePath().normalize()
                .resolve(installedFolderName());
    }

    /** Returns the readable folder name the installed copy will use. */
    public String installedFolderName() throws ProjectException {
        return requireSession().presentation().translatedFolderName();
    }

    /** Returns the readable filename suggested for the AI request file. */
    public String aiRequestFilename() throws ProjectException {
        return requireSession().presentation().aiRequestFilename();
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
