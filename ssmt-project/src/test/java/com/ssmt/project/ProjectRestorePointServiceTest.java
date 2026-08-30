package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRestorePointServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsTheLatestExplicitRestorePoint() throws Exception {
        Path projectFile = temporaryDirectory.resolve("example.ssmt.json");
        LocalizationProject project = new LocalizationProject(
                1, "example", "example.en", "Example English", List.of());
        ProjectRestorePointService restorePoints = new ProjectRestorePointService();

        restorePoints.create(projectFile, project);

        assertThat(restorePoints.load(projectFile)).contains(project);
    }
}
