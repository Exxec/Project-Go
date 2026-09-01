package com.ssmt.extractor.bytecode;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads string constants from class bytes without defining or loading classes.
 * Real mods almost always ship compiled code as a single {@code .jar} rather
 * than loose {@code .class} files (a jar is nearly always what {@code
 * mod_info.json}'s {@code jars} entry actually points to), so both forms are
 * supported: {@code .jar} is read as a zip and every {@code .class} entry
 * inside it is visited the same way a standalone {@code .class} file is.
 */
public final class ClassStringExtractor implements FileExtractor {

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Path fileName = sourceFile.getFileName();
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toString().toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".class") || lowerCaseName.endsWith(".jar");
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        Objects.requireNonNull(request, "request must not be null");
        if (!supports(request.sourceFile())) {
            throw new SsmtParseException("Unsupported class file", request.sourceFile());
        }
        if (!Files.isRegularFile(request.sourceFile())) {
            throw new SsmtParseException("Missing class file", request.sourceFile());
        }

        // supports() above already confirmed the file has a name ending in
        // .class or .jar; checking the full path's own string form (rather
        // than re-deriving getFileName(), which SpotBugs cannot prove is
        // non-null here) reaches the same answer without redereferencing it.
        boolean isJar = request.sourceFile().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
        List<ExtractedString> extracted = isJar
                ? extractJar(request)
                : extractClassFile(request);
        extracted.sort(Comparator.comparing(ExtractedString::key));
        return List.copyOf(extracted);
    }

    private static List<ExtractedString> extractClassFile(ExtractionRequest request)
            throws SsmtParseException {
        Path classFile = request.sourceFile();
        List<ExtractedString> extracted = new ArrayList<>();
        try (InputStream input = Files.newInputStream(classFile)) {
            readClass(request, input, extracted);
        } catch (IOException exception) {
            throw new SsmtParseException(
                    "Malformed class file: " + exception.getMessage(), classFile, exception);
        } catch (RuntimeException exception) {
            // Reason: ASM may surface several unchecked exception types for
            // adversarial/truncated bytes. This parser trust boundary converts
            // all of them into the toolkit's typed, localized parse failure.
            throw new SsmtParseException(
                    "Malformed class file: " + exception.getMessage(), classFile, exception);
        }
        return extracted;
    }

    private static List<ExtractedString> extractJar(ExtractionRequest request)
            throws SsmtParseException {
        Path jarFile = request.sourceFile();
        List<ExtractedString> extracted = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            List<? extends ZipEntry> classEntries = zip.stream()
                    .filter(entry -> !entry.isDirectory()
                            && entry.getName().toLowerCase(Locale.ROOT).endsWith(".class"))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            for (ZipEntry entry : classEntries) {
                try (InputStream input = zip.getInputStream(entry)) {
                    readClass(request, input, extracted);
                } catch (IOException | RuntimeException exception) {
                    // Reason: same ASM parser trust boundary as extractClassFile,
                    // scoped to a single malformed entry so the failure names
                    // both the jar and the entry that actually broke.
                    throw new SsmtParseException(
                            "Malformed class entry in jar: " + entry.getName() + ": "
                                    + exception.getMessage(),
                            jarFile,
                            exception);
                }
            }
        } catch (IOException exception) {
            throw new SsmtParseException(
                    "Malformed jar file: " + exception.getMessage(), jarFile, exception);
        }
        return extracted;
    }

    private static void readClass(
            ExtractionRequest request, InputStream input, List<ExtractedString> extracted)
            throws IOException {
        ClassReader reader = new ClassReader(input);
        reader.accept(
                new StringCollectingVisitor(request, extracted),
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static final class StringCollectingVisitor extends ClassVisitor {

        private final ExtractionRequest request;
        private final List<ExtractedString> extracted;
        private String className = "";

        private StringCollectingVisitor(
                ExtractionRequest request,
                List<ExtractedString> extracted
        ) {
            super(Opcodes.ASM9);
            this.request = request;
            this.extracted = extracted;
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces
        ) {
            className = name;
        }

        @Override
        public FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value
        ) {
            if (value instanceof String text && !text.isEmpty()) {
                add("field:" + name + ":" + descriptor, text);
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            String methodName = name;
            String methodDescriptor = descriptor;
            return new MethodVisitor(Opcodes.ASM9) {
                private int stringOrdinal;
                private int invokeDynamicOrdinal;

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof String text && !text.isEmpty()) {
                        add(
                                "method:%s%s:ldc:%d"
                                        .formatted(methodName, methodDescriptor, stringOrdinal),
                                text);
                        stringOrdinal++;
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(
                        String name,
                        String descriptor,
                        org.objectweb.asm.Handle bootstrapMethodHandle,
                        Object... bootstrapMethodArguments) {
                    for (int index = 0; index < bootstrapMethodArguments.length; index++) {
                        Object argument = bootstrapMethodArguments[index];
                        if (argument instanceof String text && !text.isEmpty()) {
                            add("method:%s%s:indy:%d:bootstrap:%d"
                                            .formatted(
                                                    methodName,
                                                    methodDescriptor,
                                                    invokeDynamicOrdinal,
                                                    index),
                                    text);
                        }
                    }
                    invokeDynamicOrdinal++;
                }
            };
        }

        private void add(String location, String text) {
            extracted.add(new ExtractedString(
                    request.modId(),
                    request.relativeSourceFile(),
                    "class:" + className + "#" + location,
                    text,
                    -1));
        }
    }
}
