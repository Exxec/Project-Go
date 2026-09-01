package com.ssmt.extractor.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MissionTextExtractorTest {

    private final MissionTextExtractor extractor = new MissionTextExtractor();

    @Test
    void recognizesOnlyMissionTextFilesUnderAMissionDirectory() {
        assertThat(extractor.supports(
                Path.of("data/missions/aglaia/mission_text.txt"))).isTrue();
        assertThat(extractor.supports(
                Path.of("Mod/data/missions/aglaia/mission_text.txt"))).isTrue();
        assertThat(extractor.supports(
                Path.of("data/missions/aglaia/descriptor.json"))).isFalse();
        assertThat(extractor.supports(Path.of("data/missions/mission_text.txt"))).isFalse();
        assertThat(extractor.supports(Path.of("readme/mission_text.txt"))).isFalse();
    }

    @Test
    void extractsTheWholeFileAsOneUnit(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/missions/aglaia/mission_text.txt");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, "Paragraph one.\n\nParagraph two.");

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).singleElement().satisfies(string -> {
            assertThat(string.key()).isEqualTo("text:file");
            assertThat(string.originalText()).isEqualTo("Paragraph one.\n\nParagraph two.");
        });
    }

    @Test
    void toleratesGb18030LikeOtherExtractors(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/missions/aglaia/mission_text.txt");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.write(source, "地点: Aglaia 星系".getBytes(Charset.forName("GB18030")));

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).singleElement()
                .extracting(ExtractedString::originalText)
                .isEqualTo("地点: Aglaia 星系");
    }

    @Test
    void rejectsUnsupportedFile(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/missions/aglaia/descriptor.json");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, "{}");

        assertThatThrownBy(() ->
                        extractor.extract(new ExtractionRequest("test", modRoot, source)))
                .isInstanceOf(SsmtParseException.class);
    }
}
