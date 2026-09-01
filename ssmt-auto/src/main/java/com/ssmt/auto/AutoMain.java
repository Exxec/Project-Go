package com.ssmt.auto;

import java.io.Console;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drag-and-drop entry point for the headless localization workflow.
 */
public final class AutoMain {
    private static final Logger LOG = LoggerFactory.getLogger(AutoMain.class);

    private AutoMain() {
    }

    /**
     * Runs one automation pass.
     *
     * @param args dropped mod archive, mod_info.json, or mod directory
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--smoke-test".equals(args[0])) {
            return;
        }
        int exit = 0;
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException(
                        "Drag one mod ZIP, mod_info.json, or mod folder onto Project Go Auto.exe");
            }
            Path supplied = Path.of(args[0]).toAbsolutePath().normalize();
            AutoRunResult result = new AutoWorkflow().runDropped(supplied);
            LOG.info("{}: {}", result.status(), result.detail());
            LOG.info("Workspace: {}", result.workspace());
        } catch (Exception exception) {
            exit = 1;
            LOG.error("Project Go Auto failed: {}", exception.getMessage());
        }
        Console console = System.console();
        if (console != null && exit != 0) {
            console.readLine("Press Enter to close...");
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }
}
