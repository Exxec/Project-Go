package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.OperationCancelledException;
import com.ssmt.core.model.TranslationProvenance;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProjectTranslationCoordinatorTest {
    @Test
    void preservesAcceptedTextAppliesExplicitFuzzyAndSuppliesCompleteContext() throws Exception {
        LocalizationProject project = new LocalizationProject(1, "mod.id", "patch", "Patch",
                List.of(
                        new ProjectEntry(Path.of("data/a.json"), "/accepted", "甲", "Accepted",
                                TranslationProvenance.HUMAN_EDITED),
                        new ProjectEntry(Path.of("data/b.csv"), "row/name", "乙", ""),
                        new ProjectEntry(Path.of("data/c.json"), "/new", "丙", "")));
        final String[] context = new String[1];
        ProjectTranslationResult result = new ProjectTranslationCoordinator().translate(
                project, ProjectTranslationSettings.conservativeZhToEn(),
                Map.of("data/b.csv#row/name", "Accepted fuzzy"), request -> {
                    context[0] = request.context();
                    return new ProjectEntryTranslation("Draft", TranslationProvenance.ARGOS_TRANSLATED,
                            java.util.Optional.empty(), "Argos CPU", false);
                }, com.ssmt.core.CancellationToken.NONE);

        assertThat(result.project().entries()).extracting(ProjectEntry::translatedText)
                .containsExactly("Accepted", "Accepted fuzzy", "Draft");
        assertThat(context[0]).contains("mod=mod.id", "file=data/c.json",
                "contentType=json", "internalId=/new");
        assertThat(result.preservedEntries()).isEqualTo(1);
    }

    @Test
    void cancelsBetweenBoundedBatches() {
        LocalizationProject project = new LocalizationProject(1, "mod", "patch", "Patch",
                List.of(new ProjectEntry(Path.of("a.json"), "/1", "一", ""),
                        new ProjectEntry(Path.of("a.json"), "/2", "二", "")));
        AtomicInteger calls = new AtomicInteger();
        ProjectTranslationSettings settings = new ProjectTranslationSettings(
                "zh", "en", com.ssmt.ai.TranslationMode.LOCAL_ONLY,
                PreferredLocalProvider.ARGOS, 1, 1, java.util.OptionalLong.empty(),
                "", "", true, false);

        assertThatThrownBy(() -> new ProjectTranslationCoordinator().translate(
                project, settings, Map.of(), request -> {
                    calls.incrementAndGet();
                    return new ProjectEntryTranslation("Draft", TranslationProvenance.ARGOS_TRANSLATED,
                            java.util.Optional.empty(), "CPU", false);
                }, () -> calls.get() == 1))
                .isInstanceOf(OperationCancelledException.class);
        assertThat(calls).hasValue(1);
    }
}
