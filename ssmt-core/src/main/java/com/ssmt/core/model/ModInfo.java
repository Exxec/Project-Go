package com.ssmt.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Normalized, immutable metadata for one Starsector mod.
 *
 * @param id unique mod identifier
 * @param name human-readable name
 * @param author declared author, when present
 * @param version declared version, when present
 * @param description declared description, when present
 * @param gameVersion supported Starsector version, when present
 * @param jars declared relative JAR paths
 * @param utility whether the mod declares itself a utility
 * @param dependencies dependencies in declaration order
 * @param sourceDirectory absolute, normalized source directory; never written by SSMT
 */
public record ModInfo(
        String id,
        String name,
        String author,
        String version,
        String description,
        String gameVersion,
        List<String> jars,
        boolean utility,
        List<ModDependency> dependencies,
        Path sourceDirectory
) {

    public ModInfo {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
        jars = jars == null ? List.of() : List.copyOf(jars);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
