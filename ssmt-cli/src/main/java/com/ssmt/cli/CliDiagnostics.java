package com.ssmt.cli;

/** Plain-language presentation for a failed CLI operation. */
public final class CliDiagnostics {
    private CliDiagnostics() {
    }

    /**
     * Builds a one-line failure explanation naming the operation, the
     * underlying reason, and a next action.
     *
     * @param operation short description of what was being attempted
     * @param failure the exception that ended the operation
     * @return formatted explanation, safe to print directly
     */
    public static String explain(String operation, Exception failure) {
        String reason = failure.getMessage() == null || failure.getMessage().isBlank()
                ? "No additional detail is available."
                : failure.getMessage();
        return operation + " failed: " + reason
                + " No files were written unless already logged above; check the inputs and try again.";
    }
}
