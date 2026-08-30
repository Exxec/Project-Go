package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRecoveryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void snapshotsOutsideSourceAndRecoversLatestProject() throws Exception {
        LocalizationProject project = new LocalizationProject(
                1,
                "example",
                "example.fr",
                "Example French",
                List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Hello",
                        "Bonjour")));
        Path source = temporaryDirectory.resolve("source");
        Path projectFile = temporaryDirectory.resolve("projects/example.ssmt.json");
        Path recoveryRoot = temporaryDirectory.resolve("recovery");
        ProjectRecoveryService recovery = new ProjectRecoveryService();

        Path snapshot = recovery.snapshot(recoveryRoot, projectFile, project);

        assertThat(snapshot).isRegularFile().startsWith(recoveryRoot);
        assertThat(snapshot.startsWith(source)).isFalse();
        assertThat(recovery.recover(recoveryRoot, projectFile))
                .contains(project);
    }
}
