package com.ssmt.project;

/** Persists completed bounded-batch state without writing into a source mod. */
@FunctionalInterface
public interface ProjectTranslationCheckpointSink {
    ProjectTranslationCheckpointSink NONE = (project, completedEntries) -> { };

    void save(LocalizationProject project, int completedEntries) throws ProjectException;
}
