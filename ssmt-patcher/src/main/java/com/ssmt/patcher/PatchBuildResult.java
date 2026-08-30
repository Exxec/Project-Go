package com.ssmt.patcher;

/**
 * Outcome of an incremental patch build.
 *
 * @param changed whether output content was published
 * @param artifactCount number of requested artifacts
 */
public record PatchBuildResult(boolean changed, int artifactCount) {
}
