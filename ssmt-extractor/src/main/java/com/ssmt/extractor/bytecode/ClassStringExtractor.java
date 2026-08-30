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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads string constants from class bytes without defining or loading classes.
 */
public final class ClassStringExtractor implements FileExtractor {

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Path fileName = sourceFile.getFileName();
        return fileName != null
                && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".class");
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

        List<ExtractedString> extracted = new ArrayList<>();
        try (InputStream input = Files.newInputStream(request.sourceFile())) {
            ClassReader reader = new ClassReader(input);
            reader.accept(
                    new StringCollectingVisitor(request, extracted),
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException exception) {
            throw new SsmtParseException(
                    "Malformed class file: " + exception.getMessage(),
                    request.sourceFile(),
                    exception);
        } catch (RuntimeException exception) {
            // Reason: ASM may surface several unchecked exception types for
            // adversarial/truncated bytes. This parser trust boundary converts
            // all of them into the toolkit's typed, localized parse failure.
            throw new SsmtParseException(
                    "Malformed class file: " + exception.getMessage(),
                    request.sourceFile(),
                    exception);
        }
        extracted.sort(Comparator.comparing(ExtractedString::key));
        return List.copyOf(extracted);
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
            return new MethodVisitor(Opcodes.ASM9) {
                private int stringOrdinal;

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof String text && !text.isEmpty()) {
                        add(
                                "method:%s%s:ldc:%d"
                                        .formatted(name, descriptor, stringOrdinal),
                                text);
                        stringOrdinal++;
                    }
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
