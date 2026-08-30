package com.ssmt.project;

/** Non-applying warning that a translated entry omits an approved target term. */
public record GlossaryConflict(ProjectEntry entry, GlossaryTerm term) { }
