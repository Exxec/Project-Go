package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageIdentityAuditTest {
    @TempDir Path temporary;

    private Path archive(Map<String, String> entries) throws Exception {
        Path zip = temporary.resolve("package.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zip;
    }

    @Test void identicalBytesUnderExplicitWrapperPassWithoutMutation() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("candidate"));
        Files.writeString(directory.resolve("mod_info.json"), "{}");
        Path zip = archive(Map.of("wrapper/mod_info.json", "{}"));
        byte[] before = Files.readAllBytes(zip);
        var audit = new PackageIdentityAudit();
        var result = audit.compare(directory, zip, "wrapper");
        assertThat(result.identical()).isTrue();
        assertThat(result).isEqualTo(audit.compare(directory, zip, "wrapper"));
        assertThat(Files.readAllBytes(zip)).isEqualTo(before);
        assertThat(Files.readString(directory.resolve("mod_info.json"))).isEqualTo("{}");
    }

    @Test void distinguishesMissingExtraAndChangedIncludingOutsideWrapper() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("candidate"));
        Files.writeString(directory.resolve("missing.txt"), "missing");
        Files.writeString(directory.resolve("changed.txt"), "before");
        Path zip = archive(Map.of("wrapper/changed.txt", "AFTER!",
                "wrapper/extra.txt", "extra", "outside.txt", "outside"));
        var result = new PackageIdentityAudit().compare(directory, zip, "wrapper");
        assertThat(result.identical()).isFalse();
        assertThat(result.missing()).containsExactly("missing.txt");
        assertThat(result.changed()).containsExactly("changed.txt");
        assertThat(result.extra()).containsExactly("extra.txt", "outside.txt");
    }

    @Test void rejectsUnsafeExplicitWrapper() {
        assertThatThrownBy(() -> new PackageIdentityAudit().compare(temporary,
                temporary.resolve("missing.zip"), "../wrapper"))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("Unsafe");
    }
}
