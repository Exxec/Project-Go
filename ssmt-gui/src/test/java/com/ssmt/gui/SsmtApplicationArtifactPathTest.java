package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SsmtApplicationArtifactPathTest {
    @Test
    void placesNewArtifactsInSafeSiblingWorkspace() {
        Path source = Path.of("mods", "Example Mod").toAbsolutePath();

        assertThat(SsmtApplication.artifactDirectory(
                source, null, "Example Mod"))
                .isEqualTo(Objects.requireNonNull(source.getParent())
                        .resolve("SSMT Auto - Example Mod"));
    }

    @Test
    void keepsExistingExternalProjectDirectory() {
        Path source = Path.of("mods", "Example Mod").toAbsolutePath();
        Path project = Path.of("work", "Example project.ssmt.json")
                .toAbsolutePath();

        assertThat(SsmtApplication.artifactDirectory(
                        source, project, "Example Mod"))
                .isEqualTo(project.getParent());
    }

    @Test
    void refusesToUseProjectDirectoryInsideSourceMod() {
        Path source = Path.of("mods", "Example Mod").toAbsolutePath();
        Path project = source.resolve("Example project.ssmt.json");

        assertThat(SsmtApplication.artifactDirectory(
                source, project, "Example Mod"))
                .isEqualTo(Objects.requireNonNull(source.getParent())
                        .resolve("SSMT Auto - Example Mod"));
    }

    @Test
    void sanitizesArtifactBaseName() {
        assertThat(SsmtApplication.artifactBaseName("A: Mod?"))
                .isEqualTo("A- Mod-");
    }
}
