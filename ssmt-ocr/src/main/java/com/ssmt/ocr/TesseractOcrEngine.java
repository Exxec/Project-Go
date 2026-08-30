package com.ssmt.ocr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Parses line regions from Tesseract TSV output.
 */
public final class TesseractOcrEngine implements OcrEngine {
    private static final int COLUMN_COUNT = 12;
    private final Path executable;
    private final String language;
    private final OcrProcess process;
    private final Duration timeout;

    public TesseractOcrEngine(Path executable, String language) {
        this(executable, language, TesseractOcrEngine::runProcess, Duration.ofMinutes(2));
    }

    TesseractOcrEngine(
            Path executable,
            String language,
            OcrProcess process,
            Duration timeout) {
        if (executable == null || language == null || language.isBlank()
                || process == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid Tesseract configuration");
        }
        this.executable = executable;
        this.language = language;
        this.process = process;
        this.timeout = timeout;
    }

    @Override
    public List<OcrTextRegion> extract(Path sourceImage) throws OcrException {
        Path image = sourceImage.toAbsolutePath().normalize();
        if (!Files.isRegularFile(image)) {
            throw new OcrException("OCR source image does not exist: " + image);
        }
        List<String> command = List.of(
                executable.toString(), image.toString(), "stdout", "-l", language, "tsv");
        try {
            ProcessResult result = process.execute(command, timeout);
            if (result.exitCode() != 0) {
                throw new OcrException(
                        "Tesseract failed with exit code " + result.exitCode()
                                + ": " + result.standardError());
            }
            return parse(result.standardOutput());
        } catch (IOException exception) {
            throw new OcrException("Could not execute Tesseract", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException("Tesseract was interrupted", exception);
        }
    }

    private static List<OcrTextRegion> parse(String output) throws OcrException {
        String[] lines = output.split("\\R");
        if (lines.length == 0 || !lines[0].startsWith("level\tpage_num\t")) {
            throw new OcrException("Malformed Tesseract TSV header");
        }
        Map<String, RegionBuilder> regions = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            String[] columns = lines[index].split("\\t", -1);
            if (columns.length == COLUMN_COUNT - 1) {
                continue;
            }
            if (columns.length < COLUMN_COUNT) {
                throw new OcrException("Malformed Tesseract TSV row " + (index + 1));
            }
            try {
                if (Integer.parseInt(columns[0]) != 5 || columns[11].isBlank()) {
                    continue;
                }
                double confidence = Double.parseDouble(columns[10]);
                if (confidence < 0.0) {
                    continue;
                }
                String key = String.join(":", columns[1], columns[2], columns[3], columns[4]);
                regions.computeIfAbsent(key, unused -> new RegionBuilder())
                        .add(
                                columns[11],
                                Integer.parseInt(columns[6]),
                                Integer.parseInt(columns[7]),
                                Integer.parseInt(columns[8]),
                                Integer.parseInt(columns[9]),
                                confidence);
            } catch (NumberFormatException exception) {
                throw new OcrException("Malformed numeric Tesseract TSV row " + (index + 1), exception);
            }
        }
        return regions.values().stream().map(RegionBuilder::build).toList();
    }

    private static ProcessResult runProcess(List<String> command, Duration timeout)
            throws IOException, InterruptedException {
        Process child = new ProcessBuilder(command).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> output = executor.submit(
                    () -> new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> error = executor.submit(
                    () -> new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!child.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                child.destroyForcibly();
                throw new IOException("Tesseract timed out");
            }
            return new ProcessResult(child.exitValue(), result(output), result(error));
        }
    }

    private static String result(Future<String> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            throw new IOException("Could not capture Tesseract output", exception.getCause());
        }
    }

    private static final class RegionBuilder {
        private final List<String> words = new ArrayList<>();
        private int left = Integer.MAX_VALUE;
        private int top = Integer.MAX_VALUE;
        private int right;
        private int bottom;
        private double confidence = 100.0;

        void add(String word, int x, int y, int width, int height, double wordConfidence) {
            words.add(word);
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + width);
            bottom = Math.max(bottom, y + height);
            confidence = Math.min(confidence, wordConfidence);
        }

        OcrTextRegion build() {
            return new OcrTextRegion(
                    String.join(" ", words),
                    left,
                    top,
                    right - left,
                    bottom - top,
                    confidence);
        }
    }
}
