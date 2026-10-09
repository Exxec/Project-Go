package com.ssmt.project;

import java.util.ArrayList;
import java.util.List;

/** Read-only readiness findings shared by the normal GUI and its build gate. */
public record WorkflowReview(List<Finding> findings) {
    public WorkflowReview { findings = List.copyOf(findings); }

    /** One user-readable finding, retaining previous source and translation where available. */
    public record Finding(String file, String key, String reason, String source,
            String previousSource, String previousTranslation) { }

    /** Checks the same protected placeholders as the actual builder, without changing entries. */
    public static WorkflowReview validate(LocalizationProject project) {
        var findings = new ArrayList<Finding>();
        var validator = new com.ssmt.validation.TranslationValidator();
        for (ProjectEntry entry : project.entries()) {
            String reason = "";
            if (!entry.originalText().isBlank() && entry.translatedText().isBlank()) {
                reason = "Needs translation";
            } else if (!entry.translatedText().isBlank()) {
                if (entry.key().startsWith("bf:")) {
                    if (BridgeForgePlaceholders.resolve(entry.originalText(), entry.translatedText()) == null) {
                        reason = "Protected placeholders differ";
                    }
                } else {
                    var issues = validator.validate(entry.originalText(), entry.translatedText());
                    if (!issues.isEmpty()) {
                        reason = issues.getFirst().message();
                    }
                }
            }
            if (!reason.isEmpty()) {
                findings.add(new Finding(entry.sourceFile().toString().replace('\\', '/'), entry.key(),
                        reason, entry.originalText(), "", ""));
            }
        }
        return new WorkflowReview(findings);
    }

    /** Object keys remain role-unknown until candidate-specific evidence establishes their use. */
    public static List<Finding> jsonKeys(LocalizationProject project) {
        var document = project.methodDocument();
        if (document == null) { return List.of(); }
        var findings = new ArrayList<Finding>();
        for (var unit : document.path("entries")) {
            if (unit.path("kind").asText().equals("json-key")) {
                findings.add(new Finding(unit.path("file").asText(), "bf:" + unit.path("id").asText(),
                        "JSON key role UNKNOWN: verify displayed label versus lookup identifier before use",
                        unit.path("source").asText(), "", ""));
            }
        }
        return List.copyOf(findings);
    }
}
