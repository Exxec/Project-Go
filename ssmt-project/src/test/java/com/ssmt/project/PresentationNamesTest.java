package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Naming-constitution regression suite. Hashes, UUIDs, fingerprints, project ids,
 * generated ids, and internal workspace names may exist internally, but they must
 * never become mod names, folder names, AI filenames, or normal UI labels.
 */
class PresentationNamesTest {
    /** Digest-shaped hex run: the classic accidental leak of an internal hash. */
    private static final Pattern DIGEST_RUN = Pattern.compile("[0-9a-fA-F]{16,}");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final List<Pattern> FORBIDDEN = List.of(
            DIGEST_RUN,
            UUID,
            Pattern.compile("\\.translation\\b"),
            Pattern.compile("\\.english\\b"),
            Pattern.compile("project\\.ssmt\\.json"),
            Pattern.compile("Project Go - "),
            Pattern.compile("_{2,}"),
            Pattern.compile("_en\\b"),
            Pattern.compile("-translated\\b"));
    private static final List<String> ILLEGAL_CHARACTERS = List.of(
            "<", ">", ":", "\"", "/", "\\", "|", "?", "*");

    private static SourceModIdentity identity(String name) {
        return new SourceModIdentity("a16709513_wkt", name, "Source Folder", "0.98a");
    }

    private static void assertClean(String value) {
        for (Pattern forbidden : FORBIDDEN) {
            assertThat(forbidden.matcher(value).find())
                    .as("%s must not match %s", value, forbidden)
                    .isFalse();
        }
        for (String illegal : ILLEGAL_CHARACTERS) {
            assertThat(value).doesNotContain(illegal);
        }
    }

    @Test void realWorldModTitlesProduceReadableFolderAndRequestNames() {
        for (String name : List.of(
                "Edmund Church",
                "Diable Avionics",
                "Secrets of the Frontier",
                "\u4e2d\u5fae\u5b50\u516c\u53f8",
                "My Mod (0.98a)",
                "Foo & Bar!")) {
            PresentationNames names = PresentationNames.forMod(identity(name), "en");
            assertThat(names.translatedFolderName()).isEqualTo(name + " - English");
            assertThat(names.aiRequestFilename())
                    .isEqualTo(name + " - Translate to English.json");
            assertThat(names.targetLanguageName()).isEqualTo("English");
            assertClean(names.translatedFolderName());
            assertClean(names.aiRequestFilename());
        }
    }

    @Test void derivedNamesNeverContainTheSourceModIdOrAnInternalKey() {
        PresentationNames names = PresentationNames.forMod(identity("Edmund Church"), "en");

        assertThat(names.translatedFolderName()).isEqualTo("Edmund Church - English");
        assertThat(names.aiRequestFilename())
                .isEqualTo("Edmund Church - Translate to English.json");
        assertThat(names.translatedFolderName()).doesNotContain("a16709513_wkt");
        assertThat(names.aiRequestFilename()).doesNotContain("a16709513_wkt");
        // Identity is not presentation: the display title stays exactly as declared.
        assertThat(identity("Edmund Church").originalName()).isEqualTo("Edmund Church");
    }

    @Test void authorSuppliedBracketsAndIdentifiersInsideATitleAreNotMangled() {
        PresentationNames names = PresentationNames.forMod(
                identity("Edmund Church[a16709513_wkt]"), "en");

        assertThat(names.translatedFolderName())
                .isEqualTo("Edmund Church[a16709513_wkt] - English");
    }

    @Test void illegalWindowsCharactersBecomeReadableSeparatorsNotUnderscores() {
        PresentationNames names = PresentationNames.forMod(identity("Some Mod: Redux?"), "en");

        assertThat(names.translatedFolderName()).isEqualTo("Some Mod - Redux - English");
        assertThat(names.aiRequestFilename())
                .isEqualTo("Some Mod - Redux - Translate to English.json");
        assertThat(PresentationNames.fileSystemName("A<B>C:D\"E/F\\G|H?I*J"))
                .isEqualTo("A - B - C - D - E - F - G - H - I - J");
    }

    @Test void unicodeTitlesSurviveAndAreNeverTransliteratedOrHashed() {
        PresentationNames names = PresentationNames.forMod(identity("\u4e2d\u5fae\u5b50\u516c\u53f8"), "en");

        assertThat(names.translatedFolderName()).isEqualTo("\u4e2d\u5fae\u5b50\u516c\u53f8 - English");
        assertThat(names.aiRequestFilename())
                .isEqualTo("\u4e2d\u5fae\u5b50\u516c\u53f8 - Translate to English.json");
    }

    @Test void veryLongTitlesStayBoundedReadableAndLegal() {
        String longName =
                "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa Lambda Mu";
        PresentationNames names = PresentationNames.forMod(identity(longName), "en");

        // Truncation keeps whole words: it never leaves a half-word and never adds a digest.
        assertThat(names.translatedFolderName()).isEqualTo(
                "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa - English");
        assertThat(names.translatedFolderName()).doesNotContain("Lamb");
        assertThat(names.translatedFolderName().length())
                .isLessThanOrEqualTo(PresentationNames.MAX_BASE_LENGTH + " - English".length());
        assertThat(names.translatedFolderName()).doesNotContain("  ");
        assertClean(names.translatedFolderName());
        assertClean(names.aiRequestFilename());
    }

    @Test void titlesThatSanitizeToNothingFallBackToAPlainWordNotADigest() {
        assertThat(PresentationNames.fileSystemName("???")).isEqualTo("Mod");
        assertThat(PresentationNames.fileSystemName("...")).isEqualTo("Mod");
        assertThat(PresentationNames.fileSystemName("   ")).isEqualTo("Mod");
        assertThat(PresentationNames.fileSystemName(null)).isEqualTo("Mod");
        PresentationNames names = PresentationNames.forMod(identity("???"), "en");
        assertThat(names.translatedFolderName()).isEqualTo("Mod - English");
    }

    @Test void windowsReservedDeviceNamesArePrefixedSoTheyRemainUsable() {
        assertThat(PresentationNames.fileSystemName("CON")).isEqualTo("Mod CON");
        assertThat(PresentationNames.fileSystemName("nul.txt")).isEqualTo("Mod nul.txt");
        assertThat(PresentationNames.fileSystemName("Console")).isEqualTo("Console");
    }

    @Test void trailingDotsAndSpacesAreRemovedBecauseWindowsRejectsThem() {
        assertThat(PresentationNames.fileSystemName("Trailing. ")).isEqualTo("Trailing");
        assertThat(PresentationNames.fileSystemName("Dashes - ")).isEqualTo("Dashes");
    }

    @Test void languageCodesBecomeReadableLanguageNames() {
        assertThat(PresentationNames.languageName("en")).isEqualTo("English");
        assertThat(PresentationNames.languageName("zh")).isEqualTo("Chinese");
        assertThat(PresentationNames.languageName("zh-CN")).isEqualTo("Chinese");
        assertThat(PresentationNames.languageName("ja")).isEqualTo("Japanese");
        assertThat(PresentationNames.languageName("ru")).isEqualTo("Russian");
        assertThat(PresentationNames.languageName("tlh")).isEqualTo("Tlh");
        assertThatThrownBy(() -> PresentationNames.languageName(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void twoModsWithOneDisplayNameSharePresentationButNotIdentity() {
        SourceModIdentity firstIdentity =
                new SourceModIdentity("first.id", "Shared Mod", "One", "0.98a");
        SourceModIdentity secondIdentity =
                new SourceModIdentity("second.id", "Shared Mod", "Two", "0.98a");

        PresentationNames first = PresentationNames.forMod(firstIdentity, "en");
        PresentationNames second = PresentationNames.forMod(secondIdentity, "en");

        // Presentation is a pure value derived from the readable title, so two mods
        // that declare the same title necessarily present the same folder name.
        assertThat(first).isEqualTo(second);
        // Identity is what stays distinct: sharing a name never merges two mods.
        assertThat(firstIdentity).isNotEqualTo(secondIdentity);
        assertThat(firstIdentity.originalId()).isNotEqualTo(secondIdentity.originalId());
        assertClean(first.translatedFolderName());
        assertClean(first.aiRequestFilename());
    }
}
