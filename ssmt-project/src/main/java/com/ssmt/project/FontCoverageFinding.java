package com.ssmt.project;

import java.nio.file.Path;
import java.util.List;

/**
 * One translated entry containing characters a specific font cannot render.
 *
 * @param sourceFile mod-relative file the entry belongs to
 * @param key stable extraction key
 * @param translatedText the text that would be reinjected
 * @param missingCodepoints distinct uncovered Unicode codepoints, ascending
 */
public record FontCoverageFinding(
        Path sourceFile,
        String key,
        String translatedText,
        List<Integer> missingCodepoints) {

    public FontCoverageFinding {
        missingCodepoints = List.copyOf(missingCodepoints);
        if (missingCodepoints.isEmpty()) {
            throw new IllegalArgumentException("missingCodepoints must not be empty");
        }
    }

    /**
     * @return the missing codepoints rendered as characters, for human display
     */
    public String missingCharacters() {
        StringBuilder builder = new StringBuilder();
        for (int codepoint : missingCodepoints) {
            builder.appendCodePoint(codepoint);
        }
        return builder.toString();
    }
}
