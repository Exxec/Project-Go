package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultTranslationMemoryLocatorTest {
    @Test
    void prefersRememberedCatalogThenExecutableDirectory(@TempDir Path root) {
        Path remembered = root.resolve("elsewhere/catalog.db");
        Path bundle = root.resolve("bundle");
        Path executable = bundle.resolve("SSMT.exe");

        assertThat(DefaultTranslationMemoryLocator.resolve(
                        Optional.of(remembered.toString()),
                        Optional.of(executable.toString()),
                        root))
                .isEqualTo(remembered.toAbsolutePath().normalize());
        assertThat(DefaultTranslationMemoryLocator.resolve(
                        Optional.empty(),
                        Optional.of(executable.toString()),
                        root))
                .isEqualTo(bundle.resolve(DefaultTranslationMemoryLocator.DEFAULT_FILENAME)
                        .toAbsolutePath()
                        .normalize());
    }

    @Test
    void sharesHeadlessPerUserCatalogLocation(@TempDir Path root) {
        Path localAppData = root.resolve("LocalAppData");

        assertThat(DefaultTranslationMemoryLocator.sharedHeadlessDefault(
                        Optional.empty(),
                        Optional.of(localAppData.toString()),
                        root))
                .isEqualTo(localAppData.resolve("SSMT/ssmt-english-catalog.db")
                        .toAbsolutePath()
                        .normalize());
    }
}
