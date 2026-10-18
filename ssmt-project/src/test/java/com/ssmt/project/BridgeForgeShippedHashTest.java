package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Parity with BridgeForge's live_trust.shipped_hash on a synthetic tree (value computed by BridgeForge on 2026-10-18). */
class BridgeForgeShippedHashTest {
    static final String REFERENCE = "86b14d830db8aaf28325cfd833b0e45340868845b4ea24ca0cbff077b4823b6f";
    @TempDir Path temporary;

    static Path tree(Path root) throws IOException {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("mod_info.json", "{\"id\":\"x\",\"name\":\"X\",\"version\":\"1\",\"jars\":[\"jars/x.jar\",\"./lib/extra.jar\"]}");
        files.put("data/a.csv", "id,name\nx,y\n");
        files.put("data/Sub/b.json", "{\"a\":1}");
        files.put("data/Thumbs.db", "junk");
        files.put("data/old.bak", "junk");
        files.put("jars/x.jar", "JAR-BYTES");
        files.put("lib/extra.jar", "EXTRA-JAR");
        files.put("lib/notes.orig-backup.txt", "junk");
        files.put("graphics/p.png", "PNG");
        files.put("src/Foo.java", "class Foo {}");
        files.put("reports/r.md", "report");
        files.put("scratch/z.txt", "scratch");
        files.put("Test/x.txt", "test dir");
        files.put(".git/config", "git");
        files.put("extra_dir/keep.txt", "kept");
        files.put("extra_dir/src2/hidden.txt", "src* directory inside");
        files.put("readme.txt", "root file");
        files.put("settings.json.default", "root default");
        files.put("Thumbs.db", "root junk");
        for (var file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    @Test void matchesTheValueBridgeForgeComputesForTheSameTree() throws Exception {
        Path root = tree(temporary.resolve("mod"));
        assertThat(BridgeForgeShippedHash.collect(root).keySet()).containsExactly("data/Sub/b.json", "data/a.csv",
                "extra_dir/keep.txt", "graphics/p.png", "jars/x.jar", "lib/extra.jar", "mod_info.json", "readme.txt",
                "settings.json.default");
        assertThat(BridgeForgeShippedHash.of(root)).isEqualTo(REFERENCE);
    }

    @Test void anyShippedByteChangesTheHashAndExcludedFilesDoNot() throws Exception {
        Path root = tree(temporary.resolve("mod"));
        Files.writeString(root.resolve("reports/r.md"), "changed report");
        Files.writeString(root.resolve("src/Foo.java"), "class Foo { }");
        assertThat(BridgeForgeShippedHash.of(root)).isEqualTo(REFERENCE);
        Files.writeString(root.resolve("data/a.csv"), "id,name\nx,z\n");
        assertThat(BridgeForgeShippedHash.of(root)).isNotEqualTo(REFERENCE);
    }
}
