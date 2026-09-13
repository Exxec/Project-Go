package com.ssmt.project;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Readable names for everything a normal user can see or touch.
 *
 * <p>Naming constitution: hashes, UUIDs, fingerprints, project ids, generated
 * ids, and internal workspace names may exist internally, but they must never
 * become mod names, folder names, AI filenames, or normal UI labels. Identity is
 * not presentation, so these values are derived from {@link SourceModIdentity}
 * and are never fed back into it.</p>
 *
 * <p>Sanitization applies to filesystem names only. The display title stays
 * exactly {@link SourceModIdentity#originalName()}. A name is never lower-cased,
 * never reduced to underscores, and never replaced by a digest; when nothing
 * readable survives, a plain word is used instead.</p>
 *
 * @param targetLanguageName readable target language, such as {@code English}
 * @param translatedFolderName installed clone folder, such as
 *        {@code Edmund Church - English}
 * @param aiRequestFilename suggested AI request file, such as
 *        {@code Edmund Church - Translate to English.json}
 */
public record PresentationNames(
        String targetLanguageName,
        String translatedFolderName,
        String aiRequestFilename) {

    /** Longest readable base name; suffixes are added on top of it. */
    static final int MAX_BASE_LENGTH = 60;
    private static final String SEPARATOR = " - ";
    private static final String FALLBACK = "Mod";
    private static final Pattern ILLEGAL = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");
    private static final Pattern RUNS_OF_WHITESPACE = Pattern.compile("\\s{2,}");
    private static final Pattern RUNS_OF_SEPARATOR = Pattern.compile("(?:\\s*-\\s*){2,}");
    private static final Pattern TRAILING_JUNK = Pattern.compile("[.\\-\\s]+$");
    private static final Set<String> RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("en", "English"),
            Map.entry("zh", "Chinese"),
            Map.entry("zh-cn", "Chinese"),
            Map.entry("zh-hans", "Chinese"),
            Map.entry("zh-tw", "Chinese"),
            Map.entry("zh-hant", "Chinese"),
            Map.entry("ja", "Japanese"),
            Map.entry("ko", "Korean"),
            Map.entry("ru", "Russian"),
            Map.entry("es", "Spanish"),
            Map.entry("fr", "French"),
            Map.entry("de", "German"),
            Map.entry("it", "Italian"),
            Map.entry("pt", "Portuguese"),
            Map.entry("pl", "Polish"),
            Map.entry("tr", "Turkish"));

    public PresentationNames {
        targetLanguageName = required(targetLanguageName, "targetLanguageName");
        translatedFolderName = required(translatedFolderName, "translatedFolderName");
        aiRequestFilename = required(aiRequestFilename, "aiRequestFilename");
    }

    /**
     * Derives every user-visible name from immutable source identity.
     *
     * @param identity source metadata, never modified
     * @param targetLanguage target language code, such as {@code en}
     * @return readable folder and AI request names
     */
    public static PresentationNames forMod(SourceModIdentity identity, String targetLanguage) {
        Objects.requireNonNull(identity, "identity");
        String language = languageName(targetLanguage);
        String base = fileSystemName(identity.originalName());
        return new PresentationNames(
                language,
                base + SEPARATOR + language,
                base + " - Translate to " + language + ".json");
    }

    /**
     * Returns a readable language name for a language code.
     *
     * @param targetLanguage language code, case-insensitive
     * @return known language name, or a capitalized code when unknown
     */
    public static String languageName(String targetLanguage) {
        String code = Objects.requireNonNullElse(targetLanguage, "").strip();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("targetLanguage must not be blank");
        }
        String known = LANGUAGES.get(code.toLowerCase(Locale.ROOT));
        if (known != null) {
            return known;
        }
        return Character.toUpperCase(code.charAt(0)) + code.substring(1);
    }

    /**
     * Turns a display title into a safe, still-readable filesystem name.
     *
     * @param value display title
     * @return readable name without illegal characters, digests, or identifiers
     */
    static String fileSystemName(String value) {
        String safe = ILLEGAL.matcher(Objects.requireNonNullElse(value, "")).replaceAll(SEPARATOR);
        safe = RUNS_OF_WHITESPACE.matcher(safe).replaceAll(" ");
        safe = RUNS_OF_SEPARATOR.matcher(safe).replaceAll(SEPARATOR);
        safe = TRAILING_JUNK.matcher(safe.strip()).replaceAll("");
        safe = shorten(safe);
        if (safe.isBlank()) {
            return FALLBACK;
        }
        return RESERVED.contains(head(safe).toUpperCase(Locale.ROOT)) ? FALLBACK + " " + safe : safe;
    }

    private static String shorten(String value) {
        if (value.length() <= MAX_BASE_LENGTH) {
            return value;
        }
        String head = value.substring(0, MAX_BASE_LENGTH);
        int boundary = head.lastIndexOf(' ');
        String cut = boundary > 0 ? head.substring(0, boundary) : head;
        return TRAILING_JUNK.matcher(cut.strip()).replaceAll("");
    }

    private static String head(String value) {
        int dot = value.indexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
