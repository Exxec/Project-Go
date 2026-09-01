package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterTranslationLibraryTest {
    @Test
    void resolvesTheConfiguredLibraryBeforeTheSharedPerUserDefault(@TempDir Path root) {
        Path configured = root.resolve("custom/master.db");

        assertThat(MasterTranslationLibrary.resolve(
                        Optional.of(configured.toString()),
                        Optional.of(root.resolve("LocalAppData").toString()),
                        root))
                .isEqualTo(configured.toAbsolutePath().normalize());
        assertThat(MasterTranslationLibrary.resolve(
                        Optional.empty(),
                        Optional.of(root.resolve("LocalAppData").toString()),
                        root))
                .isEqualTo(root.resolve("LocalAppData/Project Go/project-go-catalog.db")
                        .toAbsolutePath()
                        .normalize());
    }
}
