package com.ssmt.extractor.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void supportsClassFilesCaseInsensitively() {
        assertThat(extractor.supports(Path.of("Example.CLASS"))).isTrue();
        assertThat(extractor.supports(Path.of("example.jar"))).isFalse();
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
}
