package com.ssmt.gui;

import java.util.ResourceBundle;

/**
 * Externalized desktop user-interface text.
 */
final class GuiText {
    private static final ResourceBundle TEXT =
            ResourceBundle.getBundle("com.ssmt.gui.messages");

    private GuiText() {
    }

    static String get(String key) {
        return TEXT.getString(key);
    }
}
