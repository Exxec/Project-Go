package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared, persistence-independent guards for the normal translation workflow.
 *
 * <p>Entry points may store their state differently, but they must cross these
 * same boundaries before exporting a request, accepting a response, or
 * publishing output.</p>
 */
public final class WorkflowTransitionContract {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Stable workflow phases shared by GUI, simple CLI, and Auto. */
    public enum Phase {
        INPUT_ACCEPTED,
        PROJECT_READY,
        RESPONSE_PENDING,
        RESPONSE_IMPORTED,
        OUTPUT_PUBLISHED
    }

    /**
     * Source and entry-set binding at one workflow boundary.
     *
     * @param phase current phase
     * @param sourceModId exact declared source mod id
     * @param entrySetSha256 identities and protected source text digest
     * @param entryCount current entry count
     * @param untranslatedCount nonblank source entries still missing translations
     */
    public record State(Phase phase, String sourceModId, String entrySetSha256,
            int entryCount, int untranslatedCount) {
    }

    /** Starts a transition sequence after validated input preparation. */
    public State inputAccepted(String sourceModId) {
        requireText(sourceModId, "sourceModId");
        return new State(Phase.INPUT_ACCEPTED, sourceModId, "", 0, 0);
    }

    /** Binds the refreshed or created project to the accepted source. */
    public State projectReady(State accepted, LocalizationProject project)
            throws ProjectException {
        requirePhase(accepted, Phase.INPUT_ACCEPTED);
        requireSource(accepted, project);
        return state(Phase.PROJECT_READY, project);
    }

    /** Records that an integrity-bound AI request has been exported. */
    public State responsePending(State ready, LocalizationProject project)
            throws ProjectException {
        requirePhase(ready, Phase.PROJECT_READY);
        requireBinding(ready, project);
        return state(Phase.RESPONSE_PENDING, project);
    }

    /**
     * Accepts an imported response only when protected source identities stayed
     * unchanged. A restart may import from PROJECT_READY without retaining a
     * process-local RESPONSE_PENDING value.
     */
    public State responseImported(State current, LocalizationProject before,
            LocalizationProject after) throws ProjectException {
        if (current.phase() != Phase.PROJECT_READY
                && current.phase() != Phase.RESPONSE_PENDING) {
            throw invalid("AI response cannot be imported from " + current.phase());
        }
        requireBinding(current, before);
        requireSource(current, after);
        String beforeDigest = entrySetDigest(before);
        String afterDigest = entrySetDigest(after);
        if (!beforeDigest.equals(afterDigest)
                || before.entries().size() != after.entries().size()) {
            throw invalid("AI response changed the protected project entry set");
        }
        return state(Phase.RESPONSE_IMPORTED, after);
    }

    /** Allows publication only for a complete project with the same binding. */
    public State outputPublished(State current, LocalizationProject project)
            throws ProjectException {
        if (current.phase() != Phase.PROJECT_READY
                && current.phase() != Phase.RESPONSE_IMPORTED) {
            throw invalid("Translated output cannot be published from " + current.phase());
        }
        requireBinding(current, project);
        State published = state(Phase.OUTPUT_PUBLISHED, project);
        if (published.untranslatedCount() != 0) {
            throw new ProjectException(published.untranslatedCount()
                    + " texts still need translation. Export the translation file, "
                    + "finish it, and import it before building.");
        }
        return published;
    }

    /** Verifies a persisted boundary before an entry point resumes its workflow. */
    public void verifyPersisted(State persisted, LocalizationProject project)
            throws ProjectException {
        if (persisted.phase() == Phase.INPUT_ACCEPTED) {
            throw invalid("persisted workflow state must include a prepared project");
        }
        requireBinding(persisted, project);
        State actual = state(persisted.phase(), project);
        if (persisted.untranslatedCount() != actual.untranslatedCount()) {
            throw invalid("persisted workflow translation count does not match the project");
        }
    }

    private static State state(Phase phase, LocalizationProject project) {
        int untranslated = (int) project.entries().stream()
                .filter(entry -> !entry.originalText().isBlank())
                .filter(entry -> entry.translatedText().isBlank())
                .count();
        return new State(phase, project.sourceModId(), entrySetDigest(project),
                project.entries().size(), untranslated);
    }

    private static void requireBinding(State expected, LocalizationProject project)
            throws ProjectException {
        requireSource(expected, project);
        if (expected.entryCount() != project.entries().size()
                || !expected.entrySetSha256().equals(entrySetDigest(project))) {
            throw invalid("Translation project changed after the previous workflow step");
        }
    }

    private static void requireSource(State expected, LocalizationProject project)
            throws ProjectException {
        if (!expected.sourceModId().equals(project.sourceModId())) {
            throw invalid("Translation project does not match the accepted source mod");
        }
    }

    private static void requirePhase(State state, Phase expected)
            throws ProjectException {
        if (state.phase() != expected) {
            throw invalid("Expected " + expected + " but workflow is " + state.phase());
        }
    }

    private static String entrySetDigest(LocalizationProject project) {
        String canonical = project.entries().stream().map(entry -> JSON.createArrayNode()
                        .add(entry.sourceFile().toString().replace('\\', '/'))
                        .add(entry.key())
                        .add(entry.originalText())
                        .toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ProjectException invalid(String message) {
        return new ProjectException("Invalid workflow transition: " + message);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
