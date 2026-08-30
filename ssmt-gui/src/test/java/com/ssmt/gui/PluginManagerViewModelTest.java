package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.plugin.PluginActivationResult;
import com.ssmt.plugin.PluginDescriptor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginManagerViewModelTest {
    @Test
    void discoversAndActivatesThroughServiceBoundary() throws Exception {
        PluginDescriptor descriptor = new PluginDescriptor(
                "plugin", "Plugin", "1.0.0", 1, "example.Plugin", Path.of("plugin.jar").toAbsolutePath());
        PluginManagerViewModel model = new PluginManagerViewModel(
                directory -> List.of(descriptor),
                (plugin, work, timeout) ->
                        new PluginActivationResult(plugin.id(), plugin.version()));

        model.discover(Path.of("plugins"));
        model.activate("plugin", Path.of("work"), Duration.ofSeconds(2));

        assertThat(model.plugins()).containsExactly(
                new PluginViewState(descriptor, PluginStatus.ACTIVE, "Initialized"));
    }
}
