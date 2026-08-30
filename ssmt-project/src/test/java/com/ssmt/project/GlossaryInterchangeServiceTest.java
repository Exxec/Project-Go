package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossaryInterchangeServiceTest {
    @TempDir Path temporary;

    @Test
    void roundTripsDataOnlyGlossaryAndReportsConflicts() throws Exception {
        GlossaryDocument expected = new GlossaryDocument(1, "zh", "en", List.of(
                new GlossaryTerm("星舰", "starship", "setting term")));
        Path file = temporary.resolve("glossary.json");
        GlossaryInterchangeService service = new GlossaryInterchangeService();
        service.write(file, expected);

        GlossaryDocument actual = service.read(file);
        LocalizationProject project = new LocalizationProject(1, "mod", "patch", "Patch", List.of(
                new ProjectEntry(Path.of("data/strings.json"), "name", "星舰", "vessel")));

        assertThat(actual).isEqualTo(expected);
        assertThat(new GlossaryConflictAuditor().inspect(project, actual))
                .extracting(finding -> finding.term().target())
                .containsExactly("starship");
    }
}
