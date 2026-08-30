package com.ssmt.gui;

import java.util.Arrays;
import java.util.Objects;
import javafx.application.Application;

/**
 * Plain JVM entry point that avoids JavaFX launcher's special main-class path.
 */
public final class GuiLauncher {
    private GuiLauncher() {
    }

    /**
     * Starts the desktop UI or validates a packaged image without opening it.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        if (isSmokeTest(args)) {
            verifyPackagedResources();
            return;
        }
        Application.launch(SsmtApplication.class, args);
    }

    static boolean isSmokeTest(String[] args) {
        return Arrays.asList(args).contains("--smoke-test");
    }

    private static void verifyPackagedResources() {
        Objects.requireNonNull(
                SsmtApplication.class.getResource("ssmt-icon.png"),
                "Packaged application icon is missing");
        GuiText.get("window.title");
    }
}
