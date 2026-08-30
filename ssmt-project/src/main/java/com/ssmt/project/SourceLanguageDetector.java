package com.ssmt.project;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight deterministic source-language suggestion for AI export.
 */
public final class SourceLanguageDetector {
    private static final Set<String> ENGLISH_MARKERS = Set.of(
            "a", "and", "for", "in", "is", "of", "the", "to", "with", "you");

    /**
     * Suggests a BCP-47 language code from extracted source text.
     *
     * @param entries project entries
     * @return {@code zh}, {@code ja}, {@code ko}, {@code ru}, {@code en}, or
     *         {@code und} when the script is mixed or uncertain
     */
    public String detect(List<ProjectEntry> entries) {
        long han = 0;
        long japanese = 0;
        long korean = 0;
        long cyrillic = 0;
        long letters = 0;
        int englishMarkers = 0;
        for (ProjectEntry entry : List.copyOf(entries)) {
            String text = entry.originalText();
            String[] words = text.toLowerCase(Locale.ROOT).split("[^a-z]+");
            for (String word : words) {
                if (ENGLISH_MARKERS.contains(word)) {
                    englishMarkers++;
                }
            }
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (Character.isLetter(codePoint)) {
                    letters++;
                }
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                if (script == Character.UnicodeScript.HAN) {
                    han++;
                } else if (script == Character.UnicodeScript.HIRAGANA
                        || script == Character.UnicodeScript.KATAKANA) {
                    japanese++;
                } else if (script == Character.UnicodeScript.HANGUL) {
                    korean++;
                } else if (script == Character.UnicodeScript.CYRILLIC) {
                    cyrillic++;
                }
            }
        }
        if (letters == 0) {
            return "und";
        }
        if (korean * 5 >= letters) {
            return "ko";
        }
        if (japanese > 0 && (japanese + han) * 5 >= letters) {
            return "ja";
        }
        if (han * 5 >= letters) {
            return "zh";
        }
        if (cyrillic * 2 >= letters) {
            return "ru";
        }
        return englishMarkers >= 3 ? "en" : "und";
    }
}
