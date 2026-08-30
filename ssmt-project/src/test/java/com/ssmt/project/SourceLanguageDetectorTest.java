package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SourceLanguageDetectorTest {

    @ParameterizedTest
    @CsvSource({
        "'The ship is ready for you and the fleet',en",
        "'舰队已经准备完毕',zh",
        "'艦隊の準備ができました',ja",
        "'함대가 준비되었습니다',ko",
        "'Флот готов к бою',ru",
        "'Bonjour commandant',und"
    })
    void suggestsLanguageWithoutPretendingAmbiguousLatinIsKnown(
            String text,
            String expected) {
        ProjectEntry entry = new ProjectEntry(
                Path.of("data/strings.json"), "/text", text, "");

        assertThat(new SourceLanguageDetector().detect(List.of(entry)))
                .isEqualTo(expected);
    }
}
