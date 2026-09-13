package com.ssmt.gui;

import com.ssmt.project.LegacyProjectCandidate;
import com.ssmt.project.ProjectException;
import java.nio.file.Path;
import java.util.List;

/** Signals that opening an input needs an explicit old-project decision. */
final class LegacyProjectsFoundException extends ProjectException {
    private static final long serialVersionUID = 1L;
    // Local control-flow signal only; the payload never crosses a serialization boundary.
    private final transient Path input;
    private final transient List<LegacyProjectCandidate> candidates;

    LegacyProjectsFoundException(Path input, List<LegacyProjectCandidate> candidates) {
        super("Existing translation work was found and needs your choice.");
        this.input = input;
        this.candidates = List.copyOf(candidates);
    }

    Path input() { return input; }

    List<LegacyProjectCandidate> candidates() { return candidates; }
}
