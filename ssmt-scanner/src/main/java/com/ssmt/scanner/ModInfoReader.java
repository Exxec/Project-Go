package com.ssmt.scanner;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModDependency;
import com.ssmt.core.model.ModInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and normalizes a Starsector {@code mod_info.json}.
 */
public final class ModInfoReader {

    private static final String MOD_INFO_FILENAME = "mod_info.json";

    private final JsonMapper mapper;

    public ModInfoReader() {
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();
    }

    /**
     * Reads metadata without modifying the supplied mod directory.
     *
     * @param modDirectory mod root directory
     * @return normalized metadata
     * @throws SsmtParseException if metadata is absent, unreadable, or invalid
     */
    public ModInfo read(Path modDirectory) throws SsmtParseException {
        Path normalizedDirectory = modDirectory.toAbsolutePath().normalize();
        Path modInfoPath = normalizedDirectory.resolve(MOD_INFO_FILENAME);
        if (!Files.isRegularFile(modInfoPath)) {
            throw new SsmtParseException("Missing " + MOD_INFO_FILENAME, normalizedDirectory);
        }

        ModInfoJson raw;
        try {
            raw = mapper.readValue(modInfoPath.toFile(), ModInfoJson.class);
        } catch (JsonProcessingException exception) {
            int line = exception.getLocation() == null
                    ? -1
                    : Math.toIntExact(exception.getLocation().getLineNr());
            throw new SsmtParseException(
                    "Malformed " + MOD_INFO_FILENAME + ": " + exception.getOriginalMessage(),
                    modInfoPath,
                    line,
                    exception);
        } catch (IOException exception) {
            throw new SsmtParseException(
                    "Could not read " + MOD_INFO_FILENAME + ": " + exception.getMessage(),
                    modInfoPath,
                    exception);
        }

        if (isBlank(raw.id())) {
            throw new SsmtParseException(
                    MOD_INFO_FILENAME + " is missing required field \"id\"", modInfoPath);
        }

        List<ModDependency> dependencies = readDependencies(raw.dependencies(), modInfoPath);
        return new ModInfo(
                raw.id(),
                isBlank(raw.name()) ? raw.id() : raw.name(),
                raw.author(),
                normalizeVersion(raw.version()),
                raw.description(),
                raw.gameVersion(),
                raw.jars(),
                raw.utility(),
                dependencies,
                normalizedDirectory);
    }

    private static List<ModDependency> readDependencies(
            List<DependencyJson> rawDependencies,
            Path sourcePath
    ) throws SsmtParseException {
        if (rawDependencies == null) {
            return List.of();
        }
        List<ModDependency> dependencies = new ArrayList<>(rawDependencies.size());
        for (DependencyJson dependency : rawDependencies) {
            if (dependency == null || isBlank(dependency.id())) {
                throw new SsmtParseException(
                        "Dependency is missing required field \"id\"", sourcePath);
            }
            dependencies.add(new ModDependency(
                    dependency.id(),
                    isBlank(dependency.name()) ? dependency.id() : dependency.name(),
                    normalizeVersion(dependency.version())));
        }
        return List.copyOf(dependencies);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeVersion(JsonNode version) {
        if (version == null || version.isNull()) {
            return null;
        }
        if (version.isValueNode()) {
            return version.asText();
        }
        if (version.isObject()) {
            List<String> parts = new ArrayList<>(3);
            for (String component : List.of("major", "minor", "patch")) {
                JsonNode value = version.get(component);
                if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                    parts.add(value.asText());
                }
            }
            if (!parts.isEmpty()) {
                return String.join(".", parts);
            }
        }
        return version.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModInfoJson(
            String id,
            String name,
            String author,
            JsonNode version,
            String description,
            String gameVersion,
            List<String> jars,
            @JsonAlias("isUtility") boolean utility,
            List<DependencyJson> dependencies
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DependencyJson(String id, String name, JsonNode version) {
    }
}
