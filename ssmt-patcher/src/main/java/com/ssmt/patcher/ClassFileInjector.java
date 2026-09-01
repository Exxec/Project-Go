package com.ssmt.patcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reinjects translated string constants into class bytes without loading the class.
 * Mirrors {@code ClassStringExtractor}: a {@code .jar} is rewritten entry by entry,
 * running the same replacement pass over every {@code .class} entry and copying every
 * other entry through byte-for-byte unchanged.
 */
public final class ClassFileInjector {

    /**
     * Builds one translated class or jar artifact.
     *
     * @param sourceRoot source mod root
     * @param replacements replacements for exactly one class or jar file
     * @return complete transformed artifact
     * @throws PatchBuilderException on stale, malformed, or unmatched source data
     */
    public PatchArtifact inject(Path sourceRoot, List<TranslationReplacement> replacements)
            throws PatchBuilderException {
        List<TranslationReplacement> copy = List.copyOf(replacements);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("At least one replacement is required");
        }
        Path relative = copy.getFirst().sourceFile();
        if (copy.stream().anyMatch(item -> !relative.equals(item.sourceFile()))) {
            throw new IllegalArgumentException("All replacements must target one file");
        }
        String lowerCaseName = relative.toString().toLowerCase(Locale.ROOT);
        if (!lowerCaseName.endsWith(".class") && !lowerCaseName.endsWith(".jar")) {
            throw new PatchBuilderException("Unsupported class reinjection format: " + relative);
        }
        Path root = sourceRoot.toAbsolutePath().normalize();
        Path source = root.resolve(relative).normalize();
        if (!source.startsWith(root)) {
            throw new IllegalArgumentException("Source file escapes source root");
        }

        Map<String, TranslationReplacement> replacementsByKey = new HashMap<>();
        for (TranslationReplacement replacement : copy) {
            if (replacementsByKey.put(replacement.key(), replacement) != null) {
                throw new IllegalArgumentException("Duplicate replacement key " + replacement.key());
            }
        }

        return lowerCaseName.endsWith(".jar")
                ? injectJar(relative, source, replacementsByKey)
                : injectClassFile(relative, source, replacementsByKey);
    }

    private PatchArtifact injectClassFile(
            Path relative, Path source, Map<String, TranslationReplacement> replacementsByKey)
            throws PatchBuilderException {
        try {
            ClassReader reader = new ClassReader(Files.readAllBytes(source));
            ClassWriter writer = new ClassWriter(reader, 0);
            ReplacingVisitor visitor = new ReplacingVisitor(writer, replacementsByKey);
            reader.accept(visitor, 0);
            visitor.verifyNoStaleText();
            verifyAllMatched(relative, replacementsByKey.keySet(), visitor.matched());
            return new PatchArtifact(relative, writer.toByteArray());
        } catch (IOException exception) {
            throw new PatchBuilderException("Could not inject class file " + relative, exception);
        } catch (RuntimeException exception) {
            // Reason: ASM exposes malformed or adversarial class bytes through
            // multiple unchecked exception types at this parser trust boundary.
            throw new PatchBuilderException("Could not inject class file " + relative, exception);
        }
    }

    private PatchArtifact injectJar(
            Path relative, Path source, Map<String, TranslationReplacement> replacementsByKey)
            throws PatchBuilderException {
        Set<String> matchedOverall = new HashSet<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipFile zip = new ZipFile(source.toFile());
                ZipOutputStream output = new ZipOutputStream(buffer)) {
            List<? extends ZipEntry> entries = zip.stream()
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            for (ZipEntry entry : entries) {
                if (entry.isDirectory()) {
                    output.putNextEntry(new ZipEntry(entry.getName()));
                    output.closeEntry();
                    continue;
                }
                byte[] entryBytes = entry.getName().toLowerCase(Locale.ROOT).endsWith(".class")
                        ? injectClassEntry(relative, zip, entry, replacementsByKey, matchedOverall)
                        : readAllBytes(zip, entry);
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(entryBytes);
                output.closeEntry();
            }
        } catch (IOException exception) {
            throw new PatchBuilderException("Could not inject jar file " + relative, exception);
        }
        verifyAllMatched(relative, replacementsByKey.keySet(), matchedOverall);
        return new PatchArtifact(relative, buffer.toByteArray());
    }

    private byte[] injectClassEntry(
            Path jarRelative,
            ZipFile zip,
            ZipEntry entry,
            Map<String, TranslationReplacement> replacementsByKey,
            Set<String> matchedOverall) throws PatchBuilderException {
        try (InputStream input = zip.getInputStream(entry)) {
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(reader, 0);
            ReplacingVisitor visitor = new ReplacingVisitor(writer, replacementsByKey);
            reader.accept(visitor, 0);
            visitor.verifyNoStaleText();
            matchedOverall.addAll(visitor.matched());
            return writer.toByteArray();
        } catch (IOException exception) {
            throw new PatchBuilderException(
                    "Could not inject class entry " + entry.getName()
                            + " in jar " + jarRelative, exception);
        } catch (RuntimeException exception) {
            // Reason: same ASM parser trust boundary as the loose-class-file path,
            // scoped to a single malformed entry inside an otherwise valid jar.
            throw new PatchBuilderException(
                    "Could not inject class entry " + entry.getName()
                            + " in jar " + jarRelative, exception);
        }
    }

    private static byte[] readAllBytes(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static void verifyAllMatched(Path relative, Set<String> expected, Set<String> matched)
            throws PatchBuilderException {
        if (matched.containsAll(expected)) {
            return;
        }
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(matched);
        throw new PatchBuilderException(
                "Missing class string(s) in " + relative + " at " + missing);
    }

    private static final class ReplacingVisitor extends ClassVisitor {
        private final Map<String, TranslationReplacement> replacements;
        private final Set<String> matched = new HashSet<>();
        private String className = "";
        private String failure;

        private ReplacingVisitor(
                ClassVisitor delegate,
                Map<String, TranslationReplacement> replacements) {
            super(Opcodes.ASM9, delegate);
            this.replacements = replacements;
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces) {
            className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value) {
            Object output = value;
            if (value instanceof String text && !text.isEmpty()) {
                String key = "class:" + className + "#field:" + name + ":" + descriptor;
                output = replacementValue(key, text);
            }
            return super.visitField(access, name, descriptor, signature, output);
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions) {
            String methodName = name;
            String methodDescriptor = descriptor;
            MethodVisitor delegate =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM9, delegate) {
                private int stringOrdinal;
                private int invokeDynamicOrdinal;

                @Override
                public void visitLdcInsn(Object value) {
                    Object output = value;
                    if (value instanceof String text && !text.isEmpty()) {
                        String key = "class:" + className + "#method:" + name + descriptor
                                + ":ldc:" + stringOrdinal;
                        output = replacementValue(key, text);
                        stringOrdinal++;
                    }
                    super.visitLdcInsn(output);
                }

                @Override
                public void visitInvokeDynamicInsn(
                        String invokedName,
                        String invokedDescriptor,
                        org.objectweb.asm.Handle bootstrapMethodHandle,
                        Object... bootstrapMethodArguments) {
                    Object[] output = bootstrapMethodArguments.clone();
                    for (int index = 0; index < output.length; index++) {
                        if (output[index] instanceof String text && !text.isEmpty()) {
                            String key = "class:" + className + "#method:"
                                    + methodName + methodDescriptor + ":indy:"
                                    + invokeDynamicOrdinal + ":bootstrap:" + index;
                            output[index] = replacementValue(key, text);
                        }
                    }
                    invokeDynamicOrdinal++;
                    super.visitInvokeDynamicInsn(
                            invokedName,
                            invokedDescriptor,
                            bootstrapMethodHandle,
                            output);
                }
            };
        }

        private String replacementValue(String key, String currentText) {
            TranslationReplacement replacement = replacements.get(key);
            if (replacement == null) {
                return currentText;
            }
            matched.add(key);
            if (!replacement.originalText().equals(currentText)) {
                failure = "Stale class source text at " + key;
                return currentText;
            }
            return replacement.translatedText();
        }

        private void verifyNoStaleText() throws PatchBuilderException {
            if (failure != null) {
                throw new PatchBuilderException(failure);
            }
        }

        /**
         * @return replacement keys this single class actually matched. A jar may
         *     hold many classes, so "every requested key was matched somewhere"
         *     is verified once across all of them, not per class.
         */
        private Set<String> matched() {
            return Set.copyOf(matched);
        }
    }
}
