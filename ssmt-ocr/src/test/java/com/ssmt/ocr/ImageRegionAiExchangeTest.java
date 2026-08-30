package com.ssmt.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageRegionAiExchangeTest {
    private final ImageRegionAiExchange exchange = new ImageRegionAiExchange();

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsPaddedCropAndInstructionsWithoutChangingSource() throws Exception {
        Path source = solidColorImage(200, 100, Color.BLUE);
        byte[] originalHash = hash(source);
        ImageTranslation translation = new ImageTranslation(
                new OcrTextRegion("Niebo", 90, 40, 20, 10, 95.0), "Sky");

        List<RegionCropExport> exports =
                exchange.exportRegions(source, List.of(translation));

        assertThat(exports).singleElement().satisfies(export -> {
            assertThat(export.sourceRegion()).isEqualTo(translation.sourceRegion());
            assertThat(export.translatedText()).isEqualTo("Sky");
            // Region is 20x10; padding scales with region size (floor 16px), so
            // padded crop must be strictly larger than the raw region on all sides
            // that don't hit the image bounds.
            assertThat(export.cropLeft()).isLessThan(90);
            assertThat(export.cropTop()).isLessThan(40);
            assertThat(export.cropWidth()).isGreaterThan(20);
            assertThat(export.cropHeight()).isGreaterThan(10);
            assertThat(export.instructions())
                    .contains("Niebo")
                    .contains("Sky")
                    .contains(export.cropWidth() + " x " + export.cropHeight());
        });
        BufferedImage crop = ImageIO.read(new ByteArrayInputStream(exports.getFirst().pngBytes()));
        assertThat(crop.getWidth()).isEqualTo(exports.getFirst().cropWidth());
        assertThat(crop.getHeight()).isEqualTo(exports.getFirst().cropHeight());
        assertThat(hash(source)).isEqualTo(originalHash);
    }

    @Test
    void clampsPaddedCropToImageBoundsNearEdges() throws Exception {
        Path source = solidColorImage(30, 30, Color.GREEN);
        ImageTranslation translation = new ImageTranslation(
                new OcrTextRegion("A", 0, 0, 10, 10, 90.0), "B");

        RegionCropExport export = exchange.exportRegions(source, List.of(translation)).getFirst();

        assertThat(export.cropLeft()).isZero();
        assertThat(export.cropTop()).isZero();
        assertThat(export.cropLeft() + export.cropWidth()).isLessThanOrEqualTo(30);
        assertThat(export.cropTop() + export.cropHeight()).isLessThanOrEqualTo(30);
    }

    @Test
    void compositesRegeneratedCropsWithoutChangingSource() throws Exception {
        Path source = solidColorImage(100, 60, Color.WHITE);
        byte[] originalHash = hash(source);
        ImageTranslation translation = new ImageTranslation(
                new OcrTextRegion("Niebo", 40, 20, 20, 10, 95.0), "Sky");
        RegionCropExport export = exchange.exportRegions(source, List.of(translation)).getFirst();
        byte[] regeneratedCrop = solidColorPng(export.cropWidth(), export.cropHeight(), Color.RED);
        RegeneratedRegion regenerated = new RegeneratedRegion(
                export.cropLeft(), export.cropTop(), export.cropWidth(), export.cropHeight(),
                regeneratedCrop);

        LocalizedImage composited =
                exchange.compositeRegeneratedRegions(source, List.of(regenerated));
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(composited.pngBytes()));

        assertThat(output.getWidth()).isEqualTo(100);
        assertThat(output.getRGB(export.cropLeft(), export.cropTop())).isEqualTo(Color.RED.getRGB());
        assertThat(output.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
        assertThat(hash(source)).isEqualTo(originalHash);
    }

    @Test
    void rejectsRegeneratedCropWithMismatchedDimensions() throws Exception {
        Path source = solidColorImage(100, 60, Color.WHITE);
        ImageTranslation translation = new ImageTranslation(
                new OcrTextRegion("Niebo", 40, 20, 20, 10, 95.0), "Sky");
        RegionCropExport export = exchange.exportRegions(source, List.of(translation)).getFirst();
        byte[] wrongSize = solidColorPng(export.cropWidth() + 5, export.cropHeight(), Color.RED);

        assertThatThrownBy(() -> exchange.validateRegeneratedRegionDimensions(
                        wrongSize, export.cropWidth(), export.cropHeight()))
                .isInstanceOf(OcrException.class);

        RegeneratedRegion mismatched = new RegeneratedRegion(
                export.cropLeft(), export.cropTop(), export.cropWidth(), export.cropHeight(), wrongSize);
        assertThatThrownBy(() -> exchange.compositeRegeneratedRegions(source, List.of(mismatched)))
                .isInstanceOf(OcrException.class);
    }

    @Test
    void rejectsOverlappingOrOutOfBoundsRegeneratedRegions() throws Exception {
        Path source = solidColorImage(50, 50, Color.WHITE);
        byte[] crop = solidColorPng(20, 20, Color.RED);

        RegeneratedRegion outside = new RegeneratedRegion(45, 45, 20, 20, crop);
        assertThatThrownBy(() -> exchange.compositeRegeneratedRegions(source, List.of(outside)))
                .isInstanceOf(OcrException.class);

        RegeneratedRegion first = new RegeneratedRegion(0, 0, 20, 20, crop);
        RegeneratedRegion overlapping = new RegeneratedRegion(10, 10, 20, 20, crop);
        assertThatThrownBy(() -> exchange.compositeRegeneratedRegions(
                        source, List.of(first, overlapping)))
                .isInstanceOf(OcrException.class);
    }

    private Path solidColorImage(int width, int height, Color color) throws Exception {
        Path path = temporaryDirectory.resolve("image-" + System.nanoTime() + ".png");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private static byte[] solidColorPng(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static byte[] hash(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }
}
