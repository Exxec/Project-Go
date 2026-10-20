package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditPrototypeTest {
    @TempDir Path directory;

    @Test void malformedMetadataAndEmptyLocaleNeverPassCoverage() throws Exception {
        Path archive = directory.resolve("mod.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("mod_info.json"));
            output.write('{');
            output.closeEntry();
            output.putNextEntry(new ZipEntry("locale.csv"));
            output.closeEntry();
        }
        assertThat(CoverageRunner.run(archive.toString(), false).getFirst().verdict())
                .isEqualTo("NOT_TESTED");
        assertThat(CoverageRunner.run(archive.toString(), true).getFirst().verdict())
                .isEqualTo("FAIL");
    }

    @Test void addedEmptyFilesAndDuplicatePathsCannotPassDriftCheck() {
        var audit = new AuditEngine();
        var original = new AuditEngine.Entry("a.txt", 0, "a".repeat(64));
        var added = new AuditEngine.Entry("b.txt", 0, "a".repeat(64));
        assertThat(audit.compare("candidate", List.of(original), List.of(original, added)).verdict())
                .isEqualTo("FAIL");
        assertThatThrownBy(() -> audit.compare("candidate", List.of(original, original), List.of(original)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
