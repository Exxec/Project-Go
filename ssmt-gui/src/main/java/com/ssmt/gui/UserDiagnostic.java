package com.ssmt.gui;

/** Plain-language presentation for a failed user operation. */
public record UserDiagnostic(String summary, String detail) {
    public static UserDiagnostic failed(String operation, Throwable failure) {
        String reason = failure == null || failure.getMessage() == null
                || failure.getMessage().isBlank()
                ? "No additional technical detail is available."
                : failure.getMessage();
        Throwable cause = failure == null ? null : failure.getCause();
        var seen = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Throwable, Boolean>());
        if (failure != null) {
            seen.add(failure);
        }
        int depth = 0;
        while (cause != null && seen.add(cause) && depth++ < 10) {
            reason += System.lineSeparator() + "Caused by " + cause.getClass().getSimpleName()
                    + ": " + java.util.Objects.toString(cause.getMessage(), "No additional detail");
            cause = cause.getCause();
        }
        return new UserDiagnostic(
                operation + " did not complete",
                reason + System.lineSeparator() + System.lineSeparator()
                        + "No source-mod files were changed. Check the selected files and settings, "
                        + "then try again. If the problem continues, export diagnostics.");
    }
}
