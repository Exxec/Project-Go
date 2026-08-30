package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

class SsmtApplicationTest {

    @Test
    void providesJavaFxApplicationEntryPoint() {
        assertThat(Application.class).isAssignableFrom(SsmtApplication.class);
        assertThat(SsmtApplication.WINDOW_TITLE).isEqualTo("Starsector Mod Toolkit");
        assertThat(SsmtApplication.class.getResource("ssmt-icon.png")).isNotNull();
    }

    @Test
    void recognizesOnlyExplicitPackagedSmokeTestArgument() {
        assertThat(GuiLauncher.isSmokeTest(new String[] {"--smoke-test"})).isTrue();
        assertThat(GuiLauncher.isSmokeTest(new String[] {"--help"})).isFalse();
    }
}
