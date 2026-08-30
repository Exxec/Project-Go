package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ClassFileInjectorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rewritesFieldAndLdcWithoutLoadingClass() throws Exception {
        Path source = temporaryDirectory.resolve("Danger.class");
        Files.write(source, dangerousClass());
        System.clearProperty("ssmt.injector.executed");
        List<TranslationReplacement> replacements = List.of(
                new TranslationReplacement(
                        Path.of("Danger.class"),
                        "class:example/Danger#field:LABEL:Ljava/lang/String;",
                        "Hello field",
                        "Bonjour champ"),
                new TranslationReplacement(
                        Path.of("Danger.class"),
                        "class:example/Danger#method:text()Ljava/lang/String;:ldc:0",
                        "Hello method",
                        "Bonjour méthode"));

        PatchArtifact artifact =
                new ClassFileInjector().inject(temporaryDirectory, replacements);

        assertThat(readStrings(artifact.content()))
                .contains("Bonjour champ", "Bonjour méthode")
                .doesNotContain("Hello field", "Hello method");
        assertThat(System.getProperty("ssmt.injector.executed")).isNull();
    }

    @Test
    void rejectsStaleOrUnknownKeys() throws Exception {
        Files.write(temporaryDirectory.resolve("Danger.class"), dangerousClass());
        TranslationReplacement stale = new TranslationReplacement(
                Path.of("Danger.class"),
                "class:example/Danger#field:LABEL:Ljava/lang/String;",
                "Old",
                "New");

        assertThatThrownBy(() ->
                        new ClassFileInjector().inject(temporaryDirectory, List.of(stale)))
                .isInstanceOf(PatchBuilderException.class);
    }

    private static byte[] dangerousClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                "example/Danger",
                null,
                "java/lang/Object",
                null);
        writer.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "LABEL",
                "Ljava/lang/String;",
                null,
                "Hello field").visitEnd();
        MethodVisitor initializer =
                writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn("ssmt.injector.executed");
        initializer.visitLdcInsn("true");
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
        MethodVisitor method =
                writer.visitMethod(Opcodes.ACC_PUBLIC, "text", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn("Hello method");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static List<String> readStrings(byte[] bytes) {
        List<String> strings = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                if (value instanceof String text) {
                    strings.add(text);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String text) {
                            strings.add(text);
                        }
                    }
                };
            }
        }, 0);
        return strings;
    }
}
