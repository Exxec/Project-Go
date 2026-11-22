package com.ssmt.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Where a P9 command may write its evidence file, and how: never over a file, never inside a mod it reads. */
final class GeneEvidenceFile {
    private GeneEvidenceFile() { }

    /** Refuses an existing target (a link included) and one inside {@code root}, which is never written. */
    static void checkTarget(Path target, Path root, String option) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Refusing to overwrite " + target);
        }
        if (target.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(option + " is inside the mod folder, which is never written: " + target);
        }
    }

    /** Writes {@code document} to a new file: CREATE_NEW, so never through a link that appeared since the check. */
    static void write(Path target, ObjectMapper json, ObjectNode document) throws IOException {
        try (OutputStream stream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            json.writerWithDefaultPrettyPrinter().writeValue(stream, document);
        }
    }

    /** Project Go's version as the evidence header's produced_by records it, without the "SSMT " prefix. */
    static String version() throws IOException {
        return new Main.VersionProvider().getVersion()[0].replaceFirst("^SSMT ", "");
    }
}
