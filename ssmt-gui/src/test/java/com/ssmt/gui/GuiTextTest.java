package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuiTextTest {

    @Test
    void loadsCoreDesktopLabelsFromBundle() {
        assertThat(GuiText.get("window.title")).isEqualTo("Starsector Mod Toolkit");
        assertThat(GuiText.get("button.build")).isEqualTo("Build Translated Clone");
        assertThat(GuiText.get("dialog.unsaved.title")).isNotBlank();
    }
}
