package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectTranslationCheckpointServiceTest {
    @TempDir Path temporary;

    @Test
    void resumesOnlyAgainstMatchingSourceIdentity() throws Exception {
        LocalizationProject source = project("Source", "");
        LocalizationProject partial = project("Source", "Draft");
        Path checkpoint = temporary.resolve("translation.checkpoint.json");
        ProjectTranslationCheckpointService service =
                new ProjectTranslationCheckpointService();
        service.save(checkpoint, partial, 1);

        assertThat(service.resume(checkpoint, source).entries().getFirst().translatedText())
                .isEqualTo("Draft");
        assertThatThrownBy(() -> service.resume(checkpoint, project("Changed", "")))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("does not match");
    }

    private static LocalizationProject project(String source, String translated) {
        return new LocalizationProject(1, "mod", "patch", "Patch", List.of(
                new ProjectEntry(Path.of("data/a.json"), "/a", source, translated)));
    }
}
