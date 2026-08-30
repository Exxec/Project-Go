package com.ssmt.core.model;

import java.util.Objects;

/**
 * A dependency declared in a Starsector {@code mod_info.json}.
 *
 * @param id required mod identifier
 * @param name human-readable dependency name
 * @param version minimum version, or {@code null} when unspecified
 */
public record ModDependency(String id, String name, String version) {

    public ModDependency {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
