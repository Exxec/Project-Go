package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.ExtractedString;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class JarEntryCoverageTest {
    @TempDir Path root;

    @Test void distinguishesSelectedEmptyAndUnsupportedJarEntries() throws Exception {
        Path jar = root.resolve("payload.jar");
        try (var output = new ZipOutputStream(Files.newOutputStream(jar))) {
            write(output, "example/Selected.class", classBytes("example/Selected"));
            write(output, "example/Empty.class", classBytes("example/Empty"));
            write(output, "example/Source.java", "class Source {}".getBytes());
            write(output, "resource.json", "{}".getBytes());
        }
        Path relative = Path.of("payload.jar");
        var report = new ExtractionReport(List.of(new ExtractedString(
                "mod", relative, "class:example/Selected#field:value", "Visible", -1)),
                List.of(), List.of());

        var coverage = new JarEntryCoverage().audit(jar, relative, report);

        assertThat(coverage).extracting(JarEntryCoverage.Entry::status)
                .containsExactly("SUPPORTED_NO_STRINGS", "EXTRACTED", "UNSUPPORTED", "UNSUPPORTED");
        assertThat(coverage).extracting(JarEntryCoverage.Entry::selectedStrings)
                .containsExactly(0, 1, 0, 0);
    }

    private static byte[] classBytes(String name) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void write(ZipOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
