package com.ssmt.extractor.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ClassStringExtractorTest {

    private static final String SIDE_EFFECT_PROPERTY = "ssmt.bytecode.test.executed";
    private final ClassStringExtractor extractor = new ClassStringExtractor();

    @AfterEach
    void clearSideEffectProperty() {
        System.clearProperty(SIDE_EFFECT_PROPERTY);
    }

    @Test
    void extractsFieldAndLdcStringsWithoutExecutingStaticInitializer(
            @TempDir Path modRoot
    ) throws Exception {
        Path source = writeClass(modRoot, classWithDangerousInitializer());
        System.clearProperty(SIDE_EFFECT_PROPERTY);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source));

        assertThat(System.getProperty(SIDE_EFFECT_PROPERTY)).isNull();
        assertThat(strings).extracting(ExtractedString::originalText)
                .contains("Constant greeting", "Hello captain", SIDE_EFFECT_PROPERTY, "executed");
        assertThat(strings).extracting(ExtractedString::key)
                .contains(
                        "class:com/example/Dangerous#field:GREETING:Ljava/lang/String;",
                        "class:com/example/Dangerous#method:speak()Ljava/lang/String;:ldc:0");
    }

    @Test
    void preservesRepeatedStringsAsDistinctDeterministicLocations(
            @TempDir Path modRoot
    ) throws Exception {
        Path source = writeClass(modRoot, classWithDangerousInitializer());

        List<ExtractedString> first =
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source));
        List<ExtractedString> second =
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source));

        assertThat(second).isEqualTo(first);
        assertThat(first).extracting(ExtractedString::key).isSorted();
    }

    @Test
    void rejectsMalformedClass(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("Broken.class");
        Files.write(source, new byte[]{0, 1, 2, 3});

        assertThatThrownBy(() ->
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source)))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Malformed class");
    }

    @Test
    void supportsClassAndJarFilesCaseInsensitively() {
        assertThat(extractor.supports(Path.of("Example.CLASS"))).isTrue();
        assertThat(extractor.supports(Path.of("example.jar"))).isTrue();
        assertThat(extractor.supports(Path.of("Example.JAR"))).isTrue();
        assertThat(extractor.supports(Path.of("example.zip"))).isFalse();
    }

    @Test
    void jarExtractsStringsFromEveryClassEntryUnderTheJarsOwnSourcePath(
            @TempDir Path modRoot
    ) throws Exception {
        Path source = writeJar(modRoot,
                Map.of(
                        "com/example/Dangerous.class", classWithDangerousInitializer(),
                        "com/example/Concatenation.class", classWithInvokeDynamicString()));

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source));

        assertThat(strings).extracting(ExtractedString::sourceFile)
                .allMatch(Path.of("jars/Example.jar")::equals);
        assertThat(strings).extracting(ExtractedString::originalText)
                .contains("Constant greeting", "Hello captain", "Status: \u0001");
        assertThat(strings).extracting(ExtractedString::key)
                .contains(
                        "class:com/example/Dangerous#field:GREETING:Ljava/lang/String;",
                        "class:com/example/Concatenation#method:status()Ljava/lang/String;"
                                + ":indy:0:bootstrap:0");
    }

    @Test
    void jarReportsWhichEntryIsMalformed(@TempDir Path modRoot) throws Exception {
        Path source = writeJar(modRoot,
                Map.of("com/example/Broken.class", new byte[]{0, 1, 2, 3}));

        assertThatThrownBy(() ->
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source)))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("com/example/Broken.class");
    }

    private static Path writeJar(Path modRoot, Map<String, byte[]> entries) throws Exception {
        Path source = modRoot.resolve("jars/Example.jar");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        try (java.util.zip.ZipOutputStream zip =
                new java.util.zip.ZipOutputStream(Files.newOutputStream(source))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return source;
    }

    @Test
    void extractsStringBootstrapArgumentsFromInvokeDynamic(@TempDir Path modRoot) throws Exception {
        Path source = writeClass(modRoot, classWithInvokeDynamicString());

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test_mod", modRoot, source));

        assertThat(strings).extracting(ExtractedString::originalText)
                .contains("Status: \u0001");
        assertThat(strings).extracting(ExtractedString::key)
                .contains("class:com/example/Concatenation#method:status()Ljava/lang/String;"
                        + ":indy:0:bootstrap:0");
    }

    private static Path writeClass(Path modRoot, byte[] bytecode) throws Exception {
        Path source = modRoot.resolve("data/jars/Dangerous.class");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.write(source, bytecode);
        return source;
    }

    private static byte[] classWithDangerousInitializer() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                "com/example/Dangerous",
                null,
                "java/lang/Object",
                null);
        writer.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "GREETING",
                "Ljava/lang/String;",
                null,
                "Constant greeting").visitEnd();

        MethodVisitor initializer =
                writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(SIDE_EFFECT_PROPERTY);
        initializer.visitLdcInsn("executed");
        initializer.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();

        MethodVisitor speak = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "speak",
                "()Ljava/lang/String;",
                null,
                null);
        speak.visitCode();
        speak.visitLdcInsn("Hello captain");
        speak.visitInsn(Opcodes.ARETURN);
        speak.visitMaxs(1, 0);
        speak.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithInvokeDynamicString() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Concatenation", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "status",
                "()Ljava/lang/String;",
                null,
                null);
        method.visitCode();
        method.visitInvokeDynamicInsn(
                "makeConcatWithConstants",
                "()Ljava/lang/String;",
                new org.objectweb.asm.Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/StringConcatFactory",
                        "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/String;"
                                + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                        false),
                "Status: \u0001");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
