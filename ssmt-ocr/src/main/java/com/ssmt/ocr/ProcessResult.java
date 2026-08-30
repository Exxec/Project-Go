package com.ssmt.ocr;

/**
 * Captured OCR subprocess outcome.
 *
 * @param exitCode process exit code
 * @param standardOutput UTF-8 standard output
 * @param standardError UTF-8 standard error
 */
public record ProcessResult(int exitCode, String standardOutput, String standardError) {
    /**
     * Validates captured streams.
     */
    public ProcessResult {
        if (standardOutput == null || standardError == null) {
            throw new IllegalArgumentException("process streams must not be null");
        }
    }
}
