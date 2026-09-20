package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceIntegrityGuardTest {
    @TempDir Path source;

    @Test void acceptsReadOnlyOperationAndReturnsItsResult() throws Exception {
        Files.writeString(source.resolve("data.txt"), "unchanged");
        assertThat(new SourceIntegrityGuard().run(source, () -> "unchanged"))
                .isEqualTo("unchanged");
    }

    @Test void rejectsSourceMutationAcrossOperation() throws Exception {
        Path file = Files.writeString(source.resolve("data.txt"), "before");
        assertThatThrownBy(() -> new SourceIntegrityGuard().run(source, () -> {
            try {
                Files.writeString(file, "after");
            } catch (java.io.IOException exception) {
                throw new ProjectException("Could not mutate test source", exception);
            }
            return null;
        })).isInstanceOf(ProjectException.class).hasMessageContaining("changed during");
    }
}
