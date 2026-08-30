package com.ssmt.validation.font;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BmFontGlyphSetTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesDeclaredCodepointsAndFaceName() throws Exception {
        Path font = writeSyntheticFont("""
                info face="Placeholder Test Font" size=-12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1 outline=0
                common lineHeight=14 base=11 scaleW=128 scaleH=128 pages=1 packed=0 alphaChnl=1 redChnl=0 greenChnl=0 blueChnl=0
                page id=0 file="placeholder_0.png"
                chars count=3
                char id=32   x=0  y=0  width=1  height=1  xoffset=0  yoffset=10  xadvance=4  page=0  chnl=15
                char id=65   x=1  y=1  width=5  height=7  xoffset=0  yoffset=3   xadvance=6  page=0  chnl=15
                char id=97   x=8  y=1  width=5  height=5  xoffset=0  yoffset=5   xadvance=6  page=0  chnl=15
                """);

        BmFontGlyphSet font1 = BmFontGlyphSet.read(font);

        assertThat(font1.faceName()).isEqualTo("Placeholder Test Font");
        assertThat(font1.glyphCount()).isEqualTo(3);
        assertThat(font1.covers(65)).isTrue();
        assertThat(font1.covers(97)).isTrue();
        assertThat(font1.covers(66)).isFalse();
    }

    @Test
    void doesNotMistakePageOrCommonIdForACharacterCodepoint() throws Exception {
        Path font = writeSyntheticFont("""
                info face="Placeholder" size=-12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1 outline=0
                common lineHeight=14 base=11 scaleW=128 scaleH=128 pages=1 packed=0 alphaChnl=1 redChnl=0 greenChnl=0 blueChnl=0
                page id=0 file="placeholder_0.png"
                chars count=1
                char id=65   x=1  y=1  width=5  height=7  xoffset=0  yoffset=3   xadvance=6  page=0  chnl=15
                """);

        BmFontGlyphSet font1 = BmFontGlyphSet.read(font);

        assertThat(font1.glyphCount()).isEqualTo(1);
        assertThat(font1.covers(0)).isFalse();
        assertThat(font1.covers(1)).isFalse();
    }

    @Test
    void findMissingReturnsDistinctSortedUncoveredCodepoints() throws Exception {
        Path font = writeSyntheticFont("""
                info face="Placeholder" size=-12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1 outline=0
                common lineHeight=14 base=11 scaleW=128 scaleH=128 pages=1 packed=0 alphaChnl=1 redChnl=0 greenChnl=0 blueChnl=0
                page id=0 file="placeholder_0.png"
                chars count=2
                char id=32   x=0  y=0  width=1  height=1  xoffset=0  yoffset=10  xadvance=4  page=0  chnl=15
                char id=65   x=1  y=1  width=5  height=7  xoffset=0  yoffset=3   xadvance=6  page=0  chnl=15
                """);
        BmFontGlyphSet font1 = BmFontGlyphSet.read(font);

        assertThat(font1.findMissing("A A")).isEmpty();
        assertThat(font1.findMissing("A中文B")).containsExactly(
                (int) 'B', 0x4e2d, 0x6587);
    }

    @Test
    void rejectsMissingOrEmptyFont() throws Exception {
        assertThatThrownBy(() -> BmFontGlyphSet.read(temporaryDirectory.resolve("missing.fnt")))
                .isInstanceOf(SsmtParseException.class);

        Path empty = writeSyntheticFont("""
                info face="Placeholder" size=-12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1 outline=0
                common lineHeight=14 base=11 scaleW=128 scaleH=128 pages=1 packed=0 alphaChnl=1 redChnl=0 greenChnl=0 blueChnl=0
                page id=0 file="placeholder_0.png"
                chars count=0
                """);
        assertThatThrownBy(() -> BmFontGlyphSet.read(empty))
                .isInstanceOf(SsmtParseException.class);
    }

    private Path writeSyntheticFont(String content) throws Exception {
        Path font = temporaryDirectory.resolve("placeholder-" + System.nanoTime() + ".fnt");
        Files.writeString(font, content, StandardCharsets.UTF_8);
        return font;
    }
}
