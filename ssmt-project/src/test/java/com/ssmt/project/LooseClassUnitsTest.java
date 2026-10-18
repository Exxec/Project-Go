package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses the repository-owned synthetic Fixture.class from the conformance jar as a loose class. */
class LooseClassUnitsTest {
    private static final Path JAR = Path.of("../fixtures/translation-conformance/input/jars/test.jar");
    @TempDir Path temporary;

    private byte[] fixtureClass() throws IOException {
        try (var jar = new java.util.zip.ZipFile(JAR.toFile());
                var input = jar.getInputStream(jar.getEntry("Fixture.class"))) {
            return input.readAllBytes();
        }
    }

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("classes"));
        Files.createDirectories(mod.resolve("src"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"cls\",\"name\":\"Cls\",\"version\":\"1\"}");
        Files.write(mod.resolve("classes/Fixture.class"), fixtureClass());
        Files.write(mod.resolve("src/Fixture.class"), fixtureClass());
        return mod;
    }

    @Test void exportsReferencedConstantStringsOfALooseClassAndSkipsExcludedFolders() throws Exception {
        var document = new BridgeForgeTranslationService().export(mod());
        assertThat(document.path("entries")).hasSize(1);
        JsonNode entry = document.path("entries").get(0);
        assertThat(entry.path("id").asText()).isEqualTo("class:classes/Fixture.class#5");
        assertThat(entry.path("kind").asText()).isEqualTo("class");
        assertThat(entry.path("source").asText()).isEqualTo("中文字符串");
        assertThat(document.path("unreadable")).isEmpty();
    }

    @Test void applyRewritesOnlyTheLiteralAndLeavesMemberNamesAndPoolIndicesAlone() throws Exception {
        Path mod = mod();
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        ((ObjectNode) document.path("entries").get(0)).put("translation", "English");
        Path out = temporary.resolve("out");
        var report = new BridgeForgeTranslationApply().apply(mod, document, out, false);
        assertThat(report.path("applied").path("class").asInt()).isEqualTo(1);
        var pool = BridgeForgeClassFile.parse(Files.readAllBytes(out.resolve("classes/Fixture.class")));
        assertThat(pool.literals()).containsExactlyEntriesOf(Map.of(5, "English"));
        assertThat(pool.utf8()).containsEntry(7, "中文成员名");
        assertThat(Files.readAllBytes(mod.resolve("classes/Fixture.class"))).isEqualTo(fixtureClass());
    }

    @Test void aCorruptLooseClassIsListedAsUnreadableWithoutStoppingTheExport() throws Exception {
        Path mod = mod();
        Files.write(mod.resolve("classes/Broken.class"), new byte[] {1, 2, 3});
        var document = new BridgeForgeTranslationService().export(mod);
        assertThat(document.path("entries")).hasSize(1);
        assertThat(document.path("unreadable").get(0).asText()).startsWith("classes/Broken.class: ");
    }
}
