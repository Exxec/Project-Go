package com.ssmt.extractor.text;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Extracts vanilla Starsector's plain-text mission briefing file as one
 * translatable unit. Every mission pairs {@code MissionDefinition.java} and
 * {@code descriptor.json} with this file under {@code data/missions/<name>/}
 * (both already unsupported by design -- one is source code, the other has
 * no verified player-visible field); unlike those, this file has no internal
 * structure to select fields from, so the entire content is the shown text.
 */
public final class MissionTextExtractor implements FileExtractor {

    private static final String FILE_NAME = "mission_text.txt";
    private static final String MISSIONS_DIRECTORY = "data/missions";

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        return matches(normalize(sourceFile));
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        Objects.requireNonNull(request, "request must not be null");
        if (!supports(request.sourceFile())) {
            throw new SsmtParseException("Unsupported mission text file", request.sourceFile());
        }
        if (!Files.isRegularFile(request.sourceFile())) {
            throw new SsmtParseException("Missing mission text file", request.sourceFile());
        }
        String text = decode(request.sourceFile());
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(new ExtractedString(
                request.modId(), request.relativeSourceFile(), "text:file", text, -1));
    }

    private static boolean matches(String normalized) {
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        if (!fileName.equals(FILE_NAME) || lastSlash < 0) {
            return false;
        }
        String missionDirectory = normalized.substring(0, lastSlash);
        int missionNameSlash = missionDirectory.lastIndexOf('/');
        if (missionNameSlash < 0) {
            return false;
        }
        String missionsRoot = missionDirectory.substring(0, missionNameSlash);
        return missionsRoot.equals(MISSIONS_DIRECTORY)
                || missionsRoot.endsWith("/" + MISSIONS_DIRECTORY);
    }

    private static String normalize(Path path) {
        return path.normalize().toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
    }

    private static String decode(Path file) throws SsmtParseException {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return decodeStrict(bytes, StandardCharsets.UTF_8);
            } catch (CharacterCodingException invalidUtf8) {
                return decodeStrict(bytes, Charset.forName("GB18030"));
            }
        } catch (IOException exception) {
            throw new SsmtParseException("Could not read mission text file", file, exception);
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }
}
