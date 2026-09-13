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

    @Test void attemptedOutputIsDurableAndSeparateFromSuccessfulDestination()
            throws Exception {
        Path settings = directory.resolve("app/settings.json");
        Path mods = Files.createDirectories(directory.resolve("Starsector/mods"));
        Path output = mods.resolve("example.english");
        var preferences = new WorkflowPreferences(settings);

        preferences.rememberAttemptedOutput(output);
        preferences.rememberModsDestination(mods);
        var restarted = new WorkflowPreferences(settings);

        assertThat(restarted.attemptedOutput()).contains(output.toAbsolutePath().normalize());
        assertThat(restarted.modsDestination()).contains(mods.toRealPath());
        Files.createDirectories(output);
        restarted.rememberSuccessfulPublication(mods, output);
        assertThat(restarted.attemptedOutput()).isEmpty();
        assertThat(restarted.modsDestination()).contains(mods.toRealPath());
    }

    @Test void onlyMatchingSuccessfulOrRecoveredOutputCanClearAttempt() throws Exception {
        Path settings = directory.resolve("app/settings.json");
        Path parent = Files.createDirectories(directory.resolve("mods"));
        Path attempted = parent.resolve("attempted.english");
        var preferences = new WorkflowPreferences(settings);
        preferences.rememberAttemptedOutput(attempted);
        byte[] before = Files.readAllBytes(settings);

        assertThatThrownBy(() -> preferences.clearAttemptedOutput(
                parent.resolve("different.english")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("does not match");
        assertThat(Files.readAllBytes(settings)).containsExactly(before);
        preferences.clearAttemptedOutput(attempted);
        assertThat(preferences.attemptedOutput()).isEmpty();
    }

    @Test void rejectsUnsafeAttemptedOutputWithoutReplacingSettings() throws Exception {
        Path settings = directory.resolve("app/settings.json");
        Path mods = Files.createDirectories(directory.resolve("mods"));
        var preferences = new WorkflowPreferences(settings);
        preferences.rememberAttemptedOutput(mods.resolve("safe.english"));
        byte[] before = Files.readAllBytes(settings);
        Path regularFile = Files.writeString(directory.resolve("not-a-directory"), "x");

        assertThatThrownBy(() -> preferences.rememberAttemptedOutput(
                regularFile.resolve("output")))
                .isInstanceOf(ProjectException.class).hasMessageContaining("real-directory");
        assertThatThrownBy(() -> preferences.rememberAttemptedOutput(
                directory.toAbsolutePath().getRoot()))
                .isInstanceOf(ProjectException.class).hasMessageContaining("filesystem root");
        assertThat(Files.readAllBytes(settings)).containsExactly(before);
    }

    @Test void ignoresInvalidStoredAttemptWithoutHidingValidDestination() throws Exception {
        Path settings = directory.resolve("app/settings.json");
        Path mods = Files.createDirectories(directory.resolve("mods"));
        Files.createDirectories(directory.resolve("app"));
        String modsJson = mods.toRealPath().toString().replace("\\", "\\\\");
        Files.writeString(settings, """
                {
                  "schemaVersion": 1,
                  "modsDestination": "PATH",
                  "attemptedOutput": "relative/output"
                }
                """.replace("PATH", modsJson));
        var preferences = new WorkflowPreferences(settings);

        assertThat(preferences.modsDestination()).contains(mods.toRealPath());
        assertThat(preferences.attemptedOutput()).isEmpty();
    }
}
