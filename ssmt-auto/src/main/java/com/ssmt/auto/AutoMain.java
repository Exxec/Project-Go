package com.ssmt.auto;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
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
     * @param args dropped mod_info.json or mod directory
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--smoke-test".equals(args[0])) {
            return;
        }
        int exit = 0;
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException(
                        "Drag one mod_info.json onto SSMT Auto.exe");
            }
            Path supplied = Path.of(args[0]).toAbsolutePath().normalize();
            Path modRoot = Files.isDirectory(supplied)
                    ? supplied
                    : supplied.getParent();
            Path suppliedName = Objects.requireNonNull(
                    supplied.getFileName(), "dropped filename");
            if (modRoot == null
                    || !"mod_info.json".equalsIgnoreCase(
                            suppliedName.toString())
                            && !Files.isDirectory(supplied)) {
                throw new IllegalArgumentException(
                        "Drop a mod_info.json file or supply its mod directory");
            }
            AutoRunResult result = new AutoWorkflow().run(modRoot);
            LOG.info("{}: {}", result.status(), result.detail());
            LOG.info("Workspace: {}", result.workspace());
        } catch (Exception exception) {
            exit = 1;
            LOG.error("SSMT Auto failed: {}", exception.getMessage());
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
