package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    @Test
    void rewritesInvokeDynamicBootstrapStrings() throws Exception {
        Files.write(temporaryDirectory.resolve("Danger.class"), dangerousClass());
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("Danger.class"),
                "class:example/Danger#method:status()Ljava/lang/String;:indy:0:bootstrap:0",
                "Status: \u0001",
                "Updated: \u0001");

        PatchArtifact artifact = new ClassFileInjector().inject(
                temporaryDirectory, List.of(replacement));

        assertThat(readStrings(artifact.content()))
                .contains("Updated: \u0001")
                .doesNotContain("Status: \u0001");
    }

    @Test
    void jarRewritesOnlyTheTargetedClassEntryAndCopiesEverythingElseUnchanged()
            throws Exception {
        writeJar(temporaryDirectory, new LinkedHashMap<>(Map.of(
                "example/Danger.class", dangerousClass(),
                "resources/notes.txt", "not a class, must pass through untouched"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        List<TranslationReplacement> replacements = List.of(
                new TranslationReplacement(
                        Path.of("jars/Example.jar"),
                        "class:example/Danger#field:LABEL:Ljava/lang/String;",
                        "Hello field",
                        "Bonjour champ"));

        PatchArtifact artifact = new ClassFileInjector().inject(
                temporaryDirectory, replacements);

        Map<String, byte[]> rewritten = readJarEntries(artifact.content());
        assertThat(readStrings(rewritten.get("example/Danger.class")))
                .contains("Bonjour champ")
                .doesNotContain("Hello field");
        assertThat(rewritten.get("resources/notes.txt"))
                .isEqualTo("not a class, must pass through untouched"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void jarRejectsStaleTextInAnEntry() throws Exception {
        writeJar(temporaryDirectory, Map.of("example/Danger.class", dangerousClass()));
        TranslationReplacement stale = new TranslationReplacement(
                Path.of("jars/Example.jar"),
                "class:example/Danger#field:LABEL:Ljava/lang/String;",
                "Old",
                "New");

        assertThatThrownBy(() ->
                        new ClassFileInjector().inject(temporaryDirectory, List.of(stale)))
                .isInstanceOf(PatchBuilderException.class);
    }

    @Test
    void jarRejectsAReplacementKeyNoEntryEverMatches() throws Exception {
        writeJar(temporaryDirectory, Map.of("example/Danger.class", dangerousClass()));
        TranslationReplacement unmatched = new TranslationReplacement(
                Path.of("jars/Example.jar"),
                "class:example/NoSuchClass#field:LABEL:Ljava/lang/String;",
                "Hello field",
                "Bonjour champ");

        assertThatThrownBy(() ->
                        new ClassFileInjector().inject(temporaryDirectory, List.of(unmatched)))
                .isInstanceOf(PatchBuilderException.class)
                .hasMessageContaining("Missing class string");
    }

    private static Path writeJar(Path root, Map<String, byte[]> entries) throws Exception {
        Path source = root.resolve("jars/Example.jar");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return source;
    }

    private static Map<String, byte[]> readJarEntries(byte[] jarBytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
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
        MethodVisitor status = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "status",
                "()Ljava/lang/String;",
                null,
                null);
        status.visitCode();
        status.visitInvokeDynamicInsn(
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
        status.visitInsn(Opcodes.ARETURN);
        status.visitMaxs(1, 1);
        status.visitEnd();
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

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            org.objectweb.asm.Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments) {
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof String text) {
                                strings.add(text);
                            }
                        }
                    }
                };
            }
        }, 0);
        return strings;
    }
}
