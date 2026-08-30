package com.ssmt.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageLocalizerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersFittedTranslationWithoutChangingSource() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        BufferedImage image = new BufferedImage(120, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ImageIO.write(image, "png", source.toFile());
        byte[] originalHash = hash(source);
        ImageRenderStyle style =
                new ImageRenderStyle(0xFFFFFFFF, 0xFF000000, "SansSerif", 8, 20);
        ImageTranslation translation = new ImageTranslation(
                new OcrTextRegion("Hello", 10, 10, 80, 25, 95.0), "Bonjour capitaine");

        LocalizedImage localized =
                new ImageLocalizer().render(source, List.of(translation), style);
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(localized.pngBytes()));

        assertThat(output.getWidth()).isEqualTo(120);
        assertThat(output.getRGB(10, 10)).isEqualTo(0xFFFFFFFF);
        assertThat(localized.pngBytes()).isNotEqualTo(Files.readAllBytes(source));
        assertThat(hash(source)).isEqualTo(originalHash);
    }

    @Test
    void rejectsOverlappingAndOutOfBoundsRegions() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), "png", source.toFile());
        ImageRenderStyle style =
                new ImageRenderStyle(0xFFFFFFFF, 0xFF000000, "SansSerif", 8, 12);
        ImageTranslation first = new ImageTranslation(
                new OcrTextRegion("a", 0, 0, 10, 10, 90.0), "A");
        ImageTranslation overlap = new ImageTranslation(
                new OcrTextRegion("b", 5, 5, 10, 10, 90.0), "B");
        ImageTranslation outside = new ImageTranslation(
                new OcrTextRegion("c", 15, 15, 10, 10, 90.0), "C");
        ImageLocalizer localizer = new ImageLocalizer();

        assertThatThrownBy(() -> localizer.render(source, List.of(first, overlap), style))
                .isInstanceOf(OcrException.class);
        assertThatThrownBy(() -> localizer.render(source, List.of(outside), style))
                .isInstanceOf(OcrException.class);
    }

    @Test
    void rendersDeterministicAdvancedPanelEffects() throws Exception {
        Path source = temporaryDirectory.resolve("panel.png");
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ImageIO.write(image, "png", source.toFile());
        ImageRenderStyle base =
                new ImageRenderStyle(0xFFFFFFFF, 0xFF000000, "SansSerif", 8, 18);
        AdvancedImageRenderStyle style = new AdvancedImageRenderStyle(
                base,
                6,
                4,
                AdvancedImageRenderStyle.HorizontalAlignment.LEFT,
                AdvancedImageRenderStyle.VerticalAlignment.BOTTOM,
                10,
                0xFF0000FF,
                1,
                0xFF808080,
                2,
                2);
        ImageTranslation translation = new ImageTranslation(
                new OcrTextRegion("Hello", 10, 10, 80, 30, 95.0), "Salut");
        ImageLocalizer localizer = new ImageLocalizer();

        LocalizedImage first = localizer.render(source, List.of(translation), style);
        LocalizedImage second = localizer.render(source, List.of(translation), style);
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(first.pngBytes()));

        assertThat(first.pngBytes()).isEqualTo(second.pngBytes());
        assertThat(output.getRGB(10, 10)).isEqualTo(Color.RED.getRGB());
        assertThat(countColor(output, 0xFF0000FF)).isPositive();
        assertThat(countColor(output, 0xFF808080)).isPositive();
    }

    @Test
    void rejectsAdvancedStyleThatCannotLeaveContentArea() {
        ImageRenderStyle base =
                new ImageRenderStyle(0xFFFFFFFF, 0xFF000000, "SansSerif", 8, 18);

        assertThatThrownBy(() -> new AdvancedImageRenderStyle(
                        base,
                        -1,
                        0,
                        AdvancedImageRenderStyle.HorizontalAlignment.CENTER,
                        AdvancedImageRenderStyle.VerticalAlignment.CENTER,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int countColor(BufferedImage image, int argb) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == argb) {
                    count++;
                }
            }
        }
        return count;
    }

    private static byte[] hash(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }
}
