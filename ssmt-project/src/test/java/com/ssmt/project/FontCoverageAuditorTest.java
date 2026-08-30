package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.validation.font.BmFontGlyphSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FontCoverageAuditorTest {
    private final FontCoverageAuditor auditor = new FontCoverageAuditor();

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsEntriesContainingCodepointsTheFontCannotRender() throws Exception {
        BmFontGlyphSet asciiOnlyFont = readAsciiOnlyFont();
        LocalizationProject project = project(
                entry("a.csv", "csv:id=1:name", "Term", "Plain Text"),
                entry("b.csv", "csv:id=2:name", "Term", "Untranslated 中文"),
                entry("c.csv", "csv:id=3:name", "Term", ""));

        List<FontCoverageFinding> findings = auditor.audit(project, asciiOnlyFont);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.sourceFile()).isEqualTo(Path.of("b.csv"));
            assertThat(finding.key()).isEqualTo("csv:id=2:name");
            assertThat(finding.missingCharacters()).isEqualTo("中文");
        });
    }

    @Test
    void reportsNothingWhenAllTranslatedTextIsCovered() throws Exception {
        BmFontGlyphSet asciiOnlyFont = readAsciiOnlyFont();
        LocalizationProject project = project(
                entry("a.csv", "csv:id=1:name", "Term", "Plain Text"));

        assertThat(auditor.audit(project, asciiOnlyFont)).isEmpty();
    }

    private BmFontGlyphSet readAsciiOnlyFont() throws Exception {
        Path font = temporaryDirectory.resolve("placeholder.fnt");
        StringBuilder builder = new StringBuilder("""
                info face="Placeholder" size=-12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1 outline=0
                common lineHeight=14 base=11 scaleW=128 scaleH=128 pages=1 packed=0 alphaChnl=1 redChnl=0 greenChnl=0 blueChnl=0
                page id=0 file="placeholder_0.png"
                chars count=95
                """);
        for (int codepoint = 32; codepoint < 127; codepoint++) {
            builder.append("char id=").append(codepoint)
                    .append("   x=0  y=0  width=1  height=1  xoffset=0  yoffset=0  xadvance=1  page=0  chnl=15\n");
        }
        Files.writeString(font, builder.toString(), StandardCharsets.UTF_8);
        return BmFontGlyphSet.read(font);
    }

    private static LocalizationProject project(ProjectEntry... entries) {
        return new LocalizationProject(1, "mod", "patch", "Patch", List.of(entries));
    }

    private static ProjectEntry entry(
            String file, String key, String source, String translated) {
        return new ProjectEntry(Path.of(file), key, source, translated);
    }
}
