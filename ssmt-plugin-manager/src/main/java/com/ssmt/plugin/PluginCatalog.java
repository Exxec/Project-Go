package com.ssmt.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers bounded compatible plugin metadata without loading classes.
 */
public final class PluginCatalog {
    private static final int API_VERSION = 1;
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_METADATA_BYTES = 64L * 1024;
    private static final String METADATA = "META-INF/ssmt-plugin.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Catalogs every direct child JAR in lexical file order.
     *
     * @param pluginDirectory plugin archive directory
     * @return descriptors sorted by plugin id
     * @throws PluginCatalogException when an archive is invalid or incompatible
     */
    public List<PluginDescriptor> discover(Path pluginDirectory) throws PluginCatalogException {
        Path root = pluginDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new PluginCatalogException("Plugin directory does not exist: " + root);
        }
        try {
            List<Path> archives;
            try (var files = Files.list(root)) {
                archives = files.filter(Files::isRegularFile)
                        .filter(PluginCatalog::isJar)
                        .sorted(Comparator.comparing(PluginCatalog::fileName))
                        .toList();
            }
            List<PluginDescriptor> descriptors = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (Path archive : archives) {
                PluginDescriptor descriptor = inspect(root, archive);
                if (!ids.add(descriptor.id())) {
                    throw new PluginCatalogException("Duplicate plugin id " + descriptor.id());
                }
                descriptors.add(descriptor);
            }
            descriptors.sort(Comparator.comparing(PluginDescriptor::id));
            return List.copyOf(descriptors);
        } catch (IOException exception) {
            throw new PluginCatalogException("Could not inspect plugin directory " + root, exception);
        }
    }

    private static PluginDescriptor inspect(Path root, Path archive)
            throws IOException, PluginCatalogException {
        Path normalized = archive.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || Files.size(normalized) > MAX_ARCHIVE_BYTES) {
            throw new PluginCatalogException("Plugin archive violates size or path limits: " + archive);
        }
        try (JarFile jar = new JarFile(normalized.toFile(), false)) {
            int count = 0;
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                count++;
                if (count > MAX_ENTRIES || unsafeEntry(entry.getName())) {
                    throw new PluginCatalogException("Unsafe plugin archive " + archive);
                }
            }
            JarEntry metadata = jar.getJarEntry(METADATA);
            if (metadata == null || metadata.getSize() > MAX_METADATA_BYTES) {
                throw new PluginCatalogException("Missing or oversized plugin metadata " + archive);
            }
            try (InputStream input = jar.getInputStream(metadata)) {
                byte[] bytes = input.readNBytes((int) MAX_METADATA_BYTES + 1);
                if (bytes.length > MAX_METADATA_BYTES) {
                    throw new PluginCatalogException("Oversized plugin metadata " + archive);
                }
                return descriptor(MAPPER.readTree(bytes), normalized);
            }
        } catch (IllegalArgumentException exception) {
            throw new PluginCatalogException("Invalid plugin metadata " + archive, exception);
        }
    }

    private static PluginDescriptor descriptor(JsonNode node, Path archive)
            throws PluginCatalogException {
        if (node == null || !node.isObject()) {
            throw new PluginCatalogException("Plugin metadata must be an object: " + archive);
        }
        int apiVersion = node.path("apiVersion").asInt(-1);
        if (apiVersion != API_VERSION) {
            throw new PluginCatalogException(
                    "Unsupported plugin API version " + apiVersion + " in " + archive);
        }
        return new PluginDescriptor(
                text(node, "id"),
                text(node, "name"),
                text(node, "version"),
                apiVersion,
                text(node, "providerClass"),
                archive);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.textValue();
    }

    private static boolean unsafeEntry(String name) {
        return name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("../")
                || name.contains("..\\");
    }

    private static boolean isJar(Path path) {
        return fileName(path).toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }
}
