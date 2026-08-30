package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticSampleProjectTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installsResettableSyntheticFixtureInSelectedWorkspace() throws Exception {
        SyntheticSampleProject.InstalledSample installed =
                SyntheticSampleProject.install(temporaryDirectory);
        Path strings = installed.sourceRoot().resolve("data/strings/strings.json");

        assertThat(installed.sourceRoot()).startsWith(temporaryDirectory);
        assertThat(Files.readString(strings)).contains("Welcome, captain!");
        Files.writeString(strings, "changed practice copy");

        SyntheticSampleProject.install(temporaryDirectory);

        assertThat(Files.readString(strings)).contains("Welcome, captain!");
    }
}
