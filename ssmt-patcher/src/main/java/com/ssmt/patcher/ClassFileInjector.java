package com.ssmt.patcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reinjects translated string constants into class bytes without loading the class.
 */
public final class ClassFileInjector {

    /**
     * Builds one translated class artifact.
     *
     * @param sourceRoot source mod root
     * @param replacements replacements for exactly one class file
     * @return complete transformed class artifact
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
        if (!relative.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".class")) {
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

        try {
            ClassReader reader = new ClassReader(Files.readAllBytes(source));
            ClassWriter writer = new ClassWriter(reader, 0);
            ReplacingVisitor visitor = new ReplacingVisitor(writer, replacementsByKey);
            reader.accept(visitor, 0);
            visitor.verifyAllMatched();
            return new PatchArtifact(relative, writer.toByteArray());
        } catch (IOException exception) {
            throw new PatchBuilderException("Could not inject class file " + relative, exception);
        } catch (RuntimeException exception) {
            // Reason: ASM exposes malformed or adversarial class bytes through
            // multiple unchecked exception types at this parser trust boundary.
            throw new PatchBuilderException("Could not inject class file " + relative, exception);
        }
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
            MethodVisitor delegate =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM9, delegate) {
                private int stringOrdinal;

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

        private void verifyAllMatched() throws PatchBuilderException {
            if (failure != null) {
                throw new PatchBuilderException(failure);
            }
            for (String key : replacements.keySet()) {
                if (!matched.contains(key)) {
                    throw new PatchBuilderException("Missing class string at " + key);
                }
            }
        }
    }
}
