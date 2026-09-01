package com.ssmt.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

/**
 * SSMT command-line process entry point.
 */
@Command(
        name = "ssmt",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "Scan and process Starsector mods without modifying their source files.",
        subcommands = {
            ScanCommand.class,
            ExtractCommand.class,
            ValidateCommand.class,
            TranslationMemoryCommand.class,
            OfflineTranslateCommand.class,
            ProjectTranslateCommand.class,
            ProjectCommand.class
        }
)
public final class Main implements Runnable {

    /** Resolves the build version without duplicating it in Java source. */
    public static final class VersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() throws java.io.IOException {
            String packaged = Main.class.getPackage().getImplementationVersion();
            if (packaged != null && !packaged.isBlank()) {
                return new String[] {"SSMT " + packaged};
            }
            java.util.Properties properties = new java.util.Properties();
            try (java.io.InputStream input = Main.class.getResourceAsStream(
                    "/ssmt-version.properties")) {
                if (input == null) {
                    throw new java.io.IOException("Missing generated SSMT version resource");
                }
                properties.load(input);
            }
            String version = properties.getProperty("version", "").strip();
            if (version.isEmpty()) {
                throw new java.io.IOException("Generated SSMT version is empty");
            }
            return new String[] {"SSMT " + version};
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
