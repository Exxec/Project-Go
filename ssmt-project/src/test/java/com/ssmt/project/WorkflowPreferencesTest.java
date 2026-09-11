package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowPreferencesTest {
    @TempDir Path directory;

    @Test void remembersOnlyAStillValidDestination() throws Exception {
        Path settings = directory.resolve("app/settings.json");
        Path mods = Files.createDirectories(directory.resolve("Starsector/mods"));
        var preferences = new WorkflowPreferences(settings);

        preferences.rememberModsDestination(mods);

        assertThat(preferences.modsDestination()).contains(mods.toRealPath());
        Files.delete(mods);
        assertThat(preferences.modsDestination()).isEmpty();
    }

    @Test void rejectsMissingDestinationWithoutReplacingSettings() throws Exception {
        Path settings = directory.resolve("app/settings.json");
        Path mods = Files.createDirectories(directory.resolve("mods"));
        var preferences = new WorkflowPreferences(settings);
        preferences.rememberModsDestination(mods);
        byte[] before = Files.readAllBytes(settings);

        assertThatThrownBy(() -> preferences.rememberModsDestination(directory.resolve("missing")))
                .isInstanceOf(ProjectException.class);
        assertThat(Files.readAllBytes(settings)).containsExactly(before);
    }
}
