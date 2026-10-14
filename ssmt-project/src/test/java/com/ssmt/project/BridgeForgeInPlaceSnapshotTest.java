package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeForgeInPlaceSnapshotTest {
    private static final List<String> FILES = List.of("data/a.csv", "data/b.csv", "data/c.csv");
    @TempDir Path temporary;

    private Path modWithThreeCsvFiles() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"snap\",\"name\":\"Snap\",\"version\":\"1\"}");
        for (String file : FILES) {
            Files.writeString(mod.resolve(file), "id,name\nx,中文" + file.charAt(5) + "\n");
        }
        return mod;
    }

    private static ObjectNode translated(Path mod) throws ProjectException {
        ObjectNode document = new BridgeForgeTranslationService().export(mod);
        for (var entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", "English");
        }
        return document;
    }

    @Test void failureOnTheThirdFileLeavesEveryFileAtItsOriginalHash() throws Exception {
        Path mod = modWithThreeCsvFiles();
        List<String> before = hashes(mod);
        ObjectNode document = translated(mod);
        AtomicInteger writes = new AtomicInteger();
        var apply = new BridgeForgeTranslationApply((target, bytes) -> {
            if (writes.incrementAndGet() == 3) {
                throw new IOException("injected failure");
            }
            TranslationSnapshot.writeAtomic(target, bytes);
        });
        assertThatThrownBy(() -> apply.apply(mod, document, null, true))
                .isInstanceOf(ProjectException.class).hasMessageContaining("injected failure");
        assertThat(hashes(mod)).isEqualTo(before);
        assertThat(TranslationSnapshot.list(mod)).isEmpty();
        try (var leftovers = Files.walk(mod)) {
            assertThat(leftovers.filter(path -> path.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }
    }

    @Test void restoreAfterSuccessfulApplyReturnsTheOriginalBytes() throws Exception {
        Path mod = modWithThreeCsvFiles();
        List<String> before = hashes(mod);
        var report = new BridgeForgeTranslationApply().apply(mod, translated(mod), null, true);
        assertThat(hashes(mod)).isNotEqualTo(before);
        assertThat(report.path("status").asText()).isEqualTo("OK");
        String stamp = report.path("snapshot").asText();
        assertThat(TranslationSnapshot.list(mod)).containsExactly(stamp);
        assertThat(Files.readString(mod.resolve(TranslationSnapshot.DIRECTORY).resolve(stamp).resolve("manifest.json")))
                .contains("data/a.csv");
        // The snapshot folder is not mod content: the leftover check ignores it.
        assertThat(new BridgeForgeTranslationService().export(mod).path("entry_count").asInt()).isZero();
        assertThat(TranslationSnapshot.restore(mod, stamp)).containsExactlyInAnyOrderElementsOf(FILES);
        assertThat(hashes(mod)).isEqualTo(before);
    }

    @Test void restoreRefusesATamperedSnapshotBeforeWritingAnything() throws Exception {
        Path mod = modWithThreeCsvFiles();
        String stamp = new BridgeForgeTranslationApply().apply(mod, translated(mod), null, true)
                .path("snapshot").asText();
        List<String> applied = hashes(mod);
        Files.write(mod.resolve(TranslationSnapshot.DIRECTORY).resolve(stamp).resolve("data/b.csv"),
                "tampered".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> TranslationSnapshot.restore(mod, stamp))
                .isInstanceOf(ProjectException.class).hasMessageContaining("recorded hash");
        assertThat(hashes(mod)).isEqualTo(applied);
        assertThatThrownBy(() -> TranslationSnapshot.restore(mod, "../x")).isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> TranslationSnapshot.restore(mod, "nope")).isInstanceOf(ProjectException.class);
    }

    private List<String> hashes(Path mod) throws IOException {
        List<String> result = new java.util.ArrayList<>();
        for (String file : FILES) {
            result.add(BridgeForgeTranslationService.hash(mod.resolve(file)));
        }
        return result;
    }
}
