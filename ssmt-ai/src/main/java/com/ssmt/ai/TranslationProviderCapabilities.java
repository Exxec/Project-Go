package com.ssmt.ai;

/** Runtime controls a local provider can actually honor. */
public record TranslationProviderCapabilities(
        boolean gpuAcceleration,
        boolean hardGpuMemoryBudget,
        boolean persistentModel) { }
