package com.ssmt.ai;

import java.util.OptionalLong;

/** Conservative resource limits for a local translation provider. */
public record TranslationResourceLimits(
        int maximumWorkerThreads,
        int maximumBatchSize,
        OptionalLong maximumGpuMemoryMiB) {

    public TranslationResourceLimits {
        if (maximumWorkerThreads < 1) {
            throw new IllegalArgumentException("maximumWorkerThreads must be positive");
        }
        if (maximumBatchSize < 1) {
            throw new IllegalArgumentException("maximumBatchSize must be positive");
        }
        maximumGpuMemoryMiB = maximumGpuMemoryMiB == null
                ? OptionalLong.empty()
                : maximumGpuMemoryMiB;
        if (maximumGpuMemoryMiB.isPresent() && maximumGpuMemoryMiB.orElseThrow() < 1) {
            throw new IllegalArgumentException("maximumGpuMemoryMiB must be positive");
        }
    }

    public static TranslationResourceLimits defaults() {
        return new TranslationResourceLimits(1, 32, OptionalLong.empty());
    }
}
