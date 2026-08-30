package com.ssmt.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Installs the bundled synthetic learning fixture into a user-owned directory. */
public final class SyntheticSampleProject {
    private static final String SAMPLE_ROOT = "ssmt-sample-project";

    private SyntheticSampleProject() {
    }

    /** Paths created for one fresh sample workspace. */
    public record InstalledSample(Path sourceRoot, Path projectFile) {
    }

    /**
     * Creates or resets the small synthetic source fixture outside real mod roots.
     *
     * @param destinationParent explicitly selected user workspace
     * @return installed source and project paths
     * @throws IOException when the workspace cannot be created
     */
    public static InstalledSample install(Path destinationParent) throws IOException {
        Path parent = destinationParent.toAbsolutePath().normalize();
        Files.createDirectories(parent);
        Path workspace = parent.resolve(SAMPLE_ROOT);
        Path source = workspace.resolve("sample-source-mod");
        copyResource("/sample-project/mod_info.json", source.resolve("mod_info.json"));
        copyResource(
                "/sample-project/data/strings/strings.json",
                source.resolve("data/strings/strings.json"));
        return new InstalledSample(source, workspace.resolve("sample.ssmt.json"));
    }

    private static void copyResource(String resource, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IOException("Sample destination has no parent: " + destination);
        }
        Files.createDirectories(parent);
        try (InputStream input = SyntheticSampleProject.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Bundled sample resource is missing: " + resource);
            }
            Files.copy(input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
