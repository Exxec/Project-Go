package com.ssmt.project;

/**
 * Summary of a localization-project build.
 *
 * @param changed whether output was republished
 * @param artifactCount translated files emitted
 */
public record ProjectBuildResult(boolean changed, int artifactCount) {
    public ProjectBuildResult {
        if (artifactCount < 0) {
            throw new IllegalArgumentException("artifactCount must not be negative");
        }
    }
}
