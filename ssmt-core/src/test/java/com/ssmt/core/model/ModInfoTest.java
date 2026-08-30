package com.ssmt.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModInfoTest {

    @Test
    void nullCollectionsBecomeImmutableEmptyCollections() {
        ModInfo mod = new ModInfo(
                "id", "Name", null, null, null, null,
                null, false, null, Path.of("mod").toAbsolutePath());

        assertThat(mod.jars()).isEmpty();
        assertThat(mod.dependencies()).isEmpty();
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        List<String> jars = new ArrayList<>(List.of("mod.jar"));
        ModInfo mod = new ModInfo(
                "id", "Name", null, null, null, null,
                jars, false, List.of(), Path.of("mod").toAbsolutePath());

        jars.add("later.jar");

        assertThat(mod.jars()).containsExactly("mod.jar");
        assertThatThrownBy(() -> mod.jars().add("forbidden.jar"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
