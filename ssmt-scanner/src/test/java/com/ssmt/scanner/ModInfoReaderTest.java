package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModInfo;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModInfoReaderTest {

    private final ModInfoReader reader = new ModInfoReader();

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsWellFormedMetadata() throws Exception {
        ModInfo mod = reader.read(modDirectory("valid-mod"));

        assertThat(mod.id()).isEqualTo("valid_mod");
        assertThat(mod.name()).isEqualTo("Valid Test Mod");
        assertThat(mod.jars()).containsExactly("valid_mod.jar");
        assertThat(mod.utility()).isTrue();
    }

    @Test
    void toleratesCommentsAndTrailingCommas() throws Exception {
        ModInfo mod = reader.read(modDirectory("lenient-mod"));

        assertThat(mod.id()).isEqualTo("lenient_mod");
        assertThat(mod.jars()).containsExactly("lenient.jar");
    }

    @Test
    void toleratesHashCommentsAndNormalizesStructuredVersion() throws Exception {
        Files.writeString(temporaryDirectory.resolve("mod_info.json"), """
                {
                  "id": "community_mod",
                  "name": "Community Mod",
                  "version": {"major": 1, "minor": 3, "patch": 1},
                  # Optional integrations are intentionally disabled.
                  "dependencies": []
                }
                """);

        ModInfo mod = reader.read(temporaryDirectory);

        assertThat(mod.version()).isEqualTo("1.3.1");
    }

    @Test
    void reportsMalformedMetadataWithLineNumber() {
        assertThatThrownBy(() -> reader.read(modDirectory("malformed-mod")))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Malformed mod_info.json")
                .satisfies(error -> assertThat(((SsmtParseException) error).lineNumber()).isPresent());
    }

    @Test
    void reportsMissingMetadata() {
        assertThatThrownBy(() -> reader.read(modDirectory("no-metadata")))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Missing mod_info.json");
    }

    @Test
    void rejectsDependencyWithoutId() {
        assertThatThrownBy(() -> reader.read(modDirectory("invalid-dependency")))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Dependency is missing");
    }

    private static Path modDirectory(String name) throws URISyntaxException {
        return Path.of(ModInfoReaderTest.class.getResource("/mods/general/" + name).toURI());
    }
}
