package com.ssmt.gui;

import com.ssmt.plugin.PluginActivationException;
import com.ssmt.plugin.PluginActivationResult;
import com.ssmt.plugin.PluginActivator;
import com.ssmt.plugin.PluginCatalog;
import com.ssmt.plugin.PluginCatalogException;
import com.ssmt.plugin.PluginDescriptor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plugin discovery and isolated activation presentation state.
 */
public final class PluginManagerViewModel {
    private final PluginDiscovery discovery;
    private final PluginActivation activation;
    private final Map<String, PluginViewState> plugins = new LinkedHashMap<>();

    public PluginManagerViewModel() {
        PluginCatalog catalog = new PluginCatalog();
        PluginActivator activator = new PluginActivator();
        discovery = catalog::discover;
        activation = activator::activate;
    }

    PluginManagerViewModel(PluginDiscovery discovery, PluginActivation activation) {
        if (discovery == null || activation == null) {
            throw new IllegalArgumentException("Plugin services must not be null");
        }
        this.discovery = discovery;
        this.activation = activation;
    }

    /**
     * Replaces catalog state with compatible discovered plugins.
     *
     * @param directory plugin directory
     * @throws PluginCatalogException on invalid archives
     */
    public void discover(Path directory) throws PluginCatalogException {
        List<PluginDescriptor> descriptors = discovery.discover(directory).stream()
                .sorted(Comparator.comparing(PluginDescriptor::id))
                .toList();
        plugins.clear();
        for (PluginDescriptor descriptor : descriptors) {
            plugins.put(
                    descriptor.id(),
                    new PluginViewState(descriptor, PluginStatus.DISCOVERED, "Compatible"));
        }
    }

    /**
     * Initializes one cataloged plugin in the isolated worker.
     *
     * @param id catalog id
     * @param workingDirectory isolated work directory
     * @param timeout activation timeout
     * @throws PluginActivationException on worker failure
     */
    public void activate(String id, Path workingDirectory, Duration timeout)
            throws PluginActivationException {
        PluginViewState state = plugins.get(id);
        if (state == null) {
            throw new IllegalArgumentException("Unknown plugin " + id);
        }
        try {
            activation.activate(state.descriptor(), workingDirectory, timeout);
            plugins.put(
                    id,
                    new PluginViewState(
                            state.descriptor(),
                            PluginStatus.ACTIVE,
                            "Initialized"));
        } catch (PluginActivationException exception) {
            plugins.put(
                    id,
                    new PluginViewState(
                            state.descriptor(),
                            PluginStatus.FAILED,
                            exception.getMessage()));
            throw exception;
        }
    }

    /**
     * @return immutable plugin states in id order
     */
    public List<PluginViewState> plugins() {
        return List.copyOf(plugins.values());
    }

    @FunctionalInterface
    interface PluginDiscovery {
        List<PluginDescriptor> discover(Path directory) throws PluginCatalogException;
    }

    @FunctionalInterface
    interface PluginActivation {
        PluginActivationResult activate(
                PluginDescriptor descriptor,
                Path workingDirectory,
                Duration timeout) throws PluginActivationException;
    }
}
