package com.ssmt.validation.font;

import com.ssmt.core.exception.SsmtParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Codepoints covered by an AngelCode BMFont text-format font, as used by
 * Starsector's bitmap UI fonts ({@code graphics/fonts/*.fnt}).
 */
public final class BmFontGlyphSet {
    private static final Pattern CHAR_ID = Pattern.compile("\\bid=(\\d+)\\b");
    private static final Pattern FACE_NAME = Pattern.compile("face=\"([^\"]*)\"");

    private final String faceName;
    private final Set<Integer> codepoints;

    private BmFontGlyphSet(String faceName, Set<Integer> codepoints) {
        this.faceName = faceName;
        this.codepoints = Set.copyOf(codepoints);
    }

    /**
     * Reads and parses a BMFont text-format {@code .fnt} descriptor.
     *
     * @param fontFile path to the {@code .fnt} file
     * @return the set of Unicode codepoints the font declares glyphs for
     * @throws SsmtParseException when the file is missing, unreadable, or declares no characters
     */
    public static BmFontGlyphSet read(Path fontFile) throws SsmtParseException {
        if (!Files.isRegularFile(fontFile)) {
            throw new SsmtParseException("Missing BMFont file", fontFile);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(fontFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SsmtParseException("Could not read BMFont file", fontFile, exception);
        }
        String faceName = "";
        Set<Integer> codepoints = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("info ")) {
                Matcher matcher = FACE_NAME.matcher(trimmed);
                if (matcher.find()) {
                    faceName = matcher.group(1);
                }
            } else if (isCharLine(trimmed)) {
                Matcher matcher = CHAR_ID.matcher(trimmed);
                if (matcher.find()) {
                    codepoints.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        if (codepoints.isEmpty()) {
            throw new SsmtParseException("BMFont file declares no characters", fontFile);
        }
        return new BmFontGlyphSet(faceName, codepoints);
    }

    private static boolean isCharLine(String trimmed) {
        return trimmed.startsWith("char ") && !trimmed.startsWith("chars ");
    }

    /**
     * @param codepoint Unicode codepoint
     * @return whether the font declares a glyph for it
     */
    public boolean covers(int codepoint) {
        return codepoints.contains(codepoint);
    }

    /**
     * @param text candidate text
     * @return distinct codepoints in {@code text} the font does not cover, in ascending order
     */
    public List<Integer> findMissing(String text) {
        return text.codePoints()
                .distinct()
                .filter(codepoint -> !covers(codepoint))
                .sorted()
                .boxed()
                .toList();
    }

    /**
     * @return the font face name declared in the {@code info} line, or blank if absent
     */
    public String faceName() {
        return faceName;
    }

    /**
     * @return the total number of distinct declared glyphs
     */
    public int glyphCount() {
        return codepoints.size();
    }
}
