package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.model.ModInfo;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class ModScannerTest {

    private final ModScanner scanner = new ModScanner();

    @Test
    void scansValidModsInDeterministicDependencyOrder() throws Exception {
        ScanReport report = scanner.scan(resourceDirectory("mods/dependency-order"));

        assertThat(report.mods())
                .extracting(ModInfo::id)
                .containsExactly("base_mod", "addon_a", "addon_b");
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void skipsInvalidFoldersAndWarnsForMissingDependencies() throws Exception {
        ScanReport report = scanner.scan(resourceDirectory("mods/general"));

        assertThat(report.mods())
                .extracting(ModInfo::id)
                .contains("valid_mod", "lenient_mod");
        assertThat(report.warnings())
                .anyMatch(warning -> warning.contains("no-metadata"))
                .anyMatch(warning -> warning.contains("malformed-mod"))
                .anyMatch(warning -> warning.contains("missing mod"));
    }

    @Test
    void detectsCyclesWithoutRecursion() throws Exception {
        assertThatThrownBy(() -> scanner.scan(resourceDirectory("mods/cycle")))
                .isInstanceOf(CyclicDependencyException.class)
                .satisfies(error -> assertThat(
                        ((CyclicDependencyException) error).unresolvedModIds())
                        .containsExactly("cycle_a", "cycle_b"));
    }

    @Test
    void doesNotModifyMetadataDuringScan() throws Exception {
        Path metadata = resourceDirectory("mods/general/valid-mod").resolve("mod_info.json");
        FileTime modifiedBefore = Files.getLastModifiedTime(metadata);
        byte[] hashBefore = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(metadata));

        scanner.scan(resourceDirectory("mods/general"));

        assertThat(Files.getLastModifiedTime(metadata)).isEqualTo(modifiedBefore);
        assertThat(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(metadata)))
                .isEqualTo(hashBefore);
    }

    private static Path resourceDirectory(String name) throws URISyntaxException {
        return Path.of(ModScannerTest.class.getClassLoader().getResource(name).toURI());
    }
}
