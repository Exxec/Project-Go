package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuiTextTest {

    @Test
    void loadsCoreDesktopLabelsFromBundle() {
        assertThat(GuiText.get("window.title")).isEqualTo("Project Go");
        assertThat(GuiText.get("button.build")).isEqualTo("Make My Personal Copy");
        assertThat(GuiText.get("dialog.unsaved.title")).isNotBlank();
    }
}
