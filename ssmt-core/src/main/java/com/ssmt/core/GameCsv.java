package com.ssmt.core;

import java.util.Objects;

/**
 * CSV rows as Starsector's loader keeps them (BridgeForge {@code docs/RC8_BEHAVIOUR.md} RC8-47).
 *
 * <p>The loader's parser (0.98.5a-RC7 {@code com.fs.starfarer.loading.void.o00000(String)}, RC8
 * {@code com.fs.starfarer.loading.G}; javap 2026-11-22) raises a comment flag when the first character of a row is
 * {@code #} and never adds that row. The test is on the raw character, before quotes are read: {@code #Atropos,atropos}
 * is dropped whatever its id, while vanilla's {@code "#Light Mortar (High Delay, Fighter)",lightmortar_fighter} and a
 * row starting {@code " #"} are kept.
 */
public final class GameCsv {

    private GameCsv() {
    }

    /**
     * Whether the game drops the record that starts at {@code position} of {@code text} as a comment. Line breaks at the
     * position (blank lines Commons CSV skipped before the record) are passed over first.
     *
     * @param text the decoded table, without a byte order mark
     * @param position the record's start, as {@code CSVRecord.getCharacterPosition()} gives it
     * @return true when the record's first raw character is {@code #}
     */
    public static boolean commentedInGame(CharSequence text, long position) {
        Objects.requireNonNull(text, "text must not be null");
        long index = Math.max(0, position);
        while (index < text.length() && (text.charAt((int) index) == '\r' || text.charAt((int) index) == '\n')) {
            index++;
        }
        return index < text.length() && text.charAt((int) index) == '#';
    }
}
