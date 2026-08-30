package com.ssmt.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesseractOcrEngineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesWordRegionsAndCombinesAdjacentText() throws Exception {
        Path image = temporaryDirectory.resolve("ui.png");
        Files.write(image, new byte[] {1});
        OcrProcess process = (command, timeout) -> new ProcessResult(0, """
                level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
                5	1	1	1	1	1	10	20	30	12	95.5	Hello
                5	1	1	1	1	2	42	20	40	12	90.0	Captain
                5	1	1	1	2	1	10	40	25	12	-1
                """, "");

        TesseractOcrEngine engine =
                new TesseractOcrEngine(Path.of("tesseract"), "eng", process, Duration.ofSeconds(5));

        assertThat(engine.extract(image))
                .containsExactly(new OcrTextRegion("Hello Captain", 10, 20, 72, 12, 90.0));
    }

    @Test
    void reportsProcessAndMalformedOutputFailures() throws Exception {
        Path image = temporaryDirectory.resolve("ui.png");
        Files.write(image, new byte[] {1});
        TesseractOcrEngine failed = new TesseractOcrEngine(
                Path.of("tesseract"),
                "eng",
                (command, timeout) -> new ProcessResult(1, "", "failure"),
                Duration.ofSeconds(5));
        TesseractOcrEngine malformed = new TesseractOcrEngine(
                Path.of("tesseract"),
                "eng",
                (command, timeout) -> new ProcessResult(0, "bad", ""),
                Duration.ofSeconds(5));

        assertThatThrownBy(() -> failed.extract(image)).isInstanceOf(OcrException.class);
        assertThatThrownBy(() -> malformed.extract(image)).isInstanceOf(OcrException.class);
    }
}
