package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.ocr.ImageRenderStyle;
import com.ssmt.ocr.OcrException;
import com.ssmt.ocr.OcrTextRegion;
import com.ssmt.ocr.RegionCropExport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageLocalizationEditorViewModelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void editsRegionsAndRendersWithoutChangingSource() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        BufferedImage image = new BufferedImage(160, 60, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", source.toFile());
        String sourceHash = sha256(source);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        model.load(source, List.of(new OcrTextRegion("Hello", 5, 5, 145, 45, 99.0)));
        model.edit(0, "Bonjour");
        Path output = temporaryDirectory.resolve("localized.png");

        model.render(output, new ImageRenderStyle(
                0xff000000, 0xffffffff, "Dialog", 8, 18));

        assertThat(output).isRegularFile();
        assertThat(model.translations().getFirst().translatedText()).isEqualTo("Bonjour");
        assertThat(sha256(source)).isEqualTo(sourceHash);
    }

    @Test
    void exportsImportsAndCompositesRegeneratedRegionsWithoutChangingSource() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        writeSolidColor(source, 120, 80, Color.WHITE);
        String sourceHash = sha256(source);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        model.load(source, List.of(new OcrTextRegion("Niebo", 40, 30, 20, 10, 95.0)));
        model.edit(0, "Sky");

        List<RegionCropExport> exports = model.exportForImageAi();
        assertThat(exports).hasSize(1);
        RegionCropExport export = exports.getFirst();
        byte[] regeneratedCrop = solidColorPng(export.cropWidth(), export.cropHeight(), Color.RED);
        model.importRegeneratedRegion(0, regeneratedCrop);
        assertThat(model.regeneratedRegionCount()).isEqualTo(1);

        Path output = temporaryDirectory.resolve("ai-localized.png");
        model.renderWithRegeneratedRegions(output);

        BufferedImage result = ImageIO.read(output.toFile());
        assertThat(result.getRGB(export.cropLeft(), export.cropTop())).isEqualTo(Color.RED.getRGB());
        assertThat(sha256(source)).isEqualTo(sourceHash);
    }

    @Test
    void rejectsRegeneratedImportWithWrongDimensions() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        writeSolidColor(source, 120, 80, Color.WHITE);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        model.load(source, List.of(new OcrTextRegion("Niebo", 40, 30, 20, 10, 95.0)));
        model.edit(0, "Sky");
        model.exportForImageAi();

        assertThatThrownBy(() -> model.importRegeneratedRegion(0, solidColorPng(5, 5, Color.RED)))
                .isInstanceOf(OcrException.class);
        assertThat(model.regeneratedRegionCount()).isZero();
    }

    @Test
    void editingARegionAfterExportInvalidatesItsPriorExport() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        writeSolidColor(source, 120, 80, Color.WHITE);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        model.load(source, List.of(new OcrTextRegion("Niebo", 40, 30, 20, 10, 95.0)));
        model.edit(0, "Sky");
        RegionCropExport export = model.exportForImageAi().getFirst();
        model.edit(0, "Heavens");

        assertThatThrownBy(() -> model.importRegeneratedRegion(
                        0, solidColorPng(export.cropWidth(), export.cropHeight(), Color.RED)))
                .isInstanceOf(OcrException.class);
    }

    @Test
    void reloadPreservesTranslationWhenAddingANewRegion() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        writeSolidColor(source, 200, 100, Color.WHITE);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        OcrTextRegion first = new OcrTextRegion("Niebo", 10, 10, 20, 10, 95.0);
        model.load(source, List.of(first));
        model.edit(0, "Sky");

        OcrTextRegion second = new OcrTextRegion("Slonce", 50, 50, 20, 10, 95.0);
        model.reload(List.of(first, second));

        assertThat(model.translations()).hasSize(2);
        assertThat(model.translations().get(0).translatedText()).isEqualTo("Sky");
        assertThat(model.translations().get(1).translatedText()).isEqualTo("Slonce");
    }

    @Test
    void reloadPreservesImportedRegeneratedArtWhenAddingANewRegion() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        writeSolidColor(source, 200, 100, Color.WHITE);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        OcrTextRegion first = new OcrTextRegion("Niebo", 10, 10, 20, 10, 95.0);
        model.load(source, List.of(first));
        model.edit(0, "Sky");
        RegionCropExport export = model.exportForImageAi().getFirst();
        model.importRegeneratedRegion(
                0, solidColorPng(export.cropWidth(), export.cropHeight(), Color.RED));

        OcrTextRegion second = new OcrTextRegion("Slonce", 50, 50, 20, 10, 95.0);
        model.reload(List.of(first, second));

        assertThat(model.regeneratedRegionCount()).isEqualTo(1);
        assertThat(model.translations().get(0).translatedText()).isEqualTo("Sky");
        Path output = temporaryDirectory.resolve("ai-localized.png");
        model.renderWithRegeneratedRegions(output);
        BufferedImage result = ImageIO.read(output.toFile());
        assertThat(result.getRGB(export.cropLeft(), export.cropTop())).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void reloadDropsPreservedDataForRegionsNoLongerPresent() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        writeSolidColor(source, 200, 100, Color.WHITE);
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        OcrTextRegion first = new OcrTextRegion("Niebo", 10, 10, 20, 10, 95.0);
        OcrTextRegion second = new OcrTextRegion("Slonce", 50, 50, 20, 10, 95.0);
        model.load(source, List.of(first, second));
        model.edit(0, "Sky");
        model.edit(1, "Sun");

        model.reload(List.of(first));

        assertThat(model.translations()).hasSize(1);
        assertThat(model.translations().getFirst().translatedText()).isEqualTo("Sky");
    }

    @Test
    void reloadRequiresSourceImageAlreadyLoaded() {
        ImageLocalizationEditorViewModel model = new ImageLocalizationEditorViewModel();
        assertThatThrownBy(() -> model.reload(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void writeSolidColor(Path destination, int width, int height, Color color)
            throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ImageIO.write(image, "png", destination.toFile());
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

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
