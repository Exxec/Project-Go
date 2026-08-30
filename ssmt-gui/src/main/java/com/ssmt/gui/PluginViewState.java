package com.ssmt.gui;

import com.ssmt.plugin.PluginDescriptor;

/**
 * Immutable plugin catalog/activation presentation state.
 *
 * @param descriptor catalog metadata
 * @param status lifecycle state
 * @param detail human-readable status detail
 */
public record PluginViewState(
        PluginDescriptor descriptor,
        PluginStatus status,
        String detail) {
    /**
     * Validates state.
     */
    public PluginViewState {
        if (descriptor == null || status == null || detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("Plugin view state must not be null or blank");
        }
    }
}
