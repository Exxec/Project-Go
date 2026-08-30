package com.ssmt.cli;

import com.ssmt.plugin.PluginCatalog;
import com.ssmt.plugin.PluginCatalogException;
import com.ssmt.plugin.PluginDescriptor;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Catalogs compatible plugin archives without loading their classes.
 */
@Command(name = "plugins", description = "Inspect compatible plugin JAR metadata.")
public final class PluginCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(PluginCommand.class);

    @Parameters(index = "0", description = "Plugin JAR directory.")
    private Path pluginDirectory;

    @Override
    public Integer call() {
        try {
            for (PluginDescriptor plugin : new PluginCatalog().discover(pluginDirectory)) {
                LOG.info("Plugin {} {} ({})", plugin.id(), plugin.version(), plugin.name());
            }
            return 0;
        } catch (PluginCatalogException exception) {
            LOG.error("Plugin discovery failed: {}", exception.getMessage());
            return 1;
        }
    }
}
