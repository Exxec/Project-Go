package com.ssmt.gui;

/** Plain-language presentation for a failed user operation. */
public record UserDiagnostic(String summary, String detail) {
    public static UserDiagnostic failed(String operation, Throwable failure) {
        String reason = failure == null || failure.getMessage() == null
                || failure.getMessage().isBlank()
                ? "No additional technical detail is available."
                : failure.getMessage();
        return new UserDiagnostic(
                operation + " did not complete",
                reason + System.lineSeparator() + System.lineSeparator()
                        + "No source-mod files were changed. Check the selected files and settings, "
                        + "then try again. If the problem continues, export diagnostics.");
    }
}
