package com.ssmt.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Prepares padded region crops and their regeneration instructions for an
 * external AI image tool, then validates and composites the regenerated
 * crops back into a copy of the source image. SSMT never calls an image
 * generation service itself — the human runs the export through whatever AI
 * tool they choose and imports the result back for review.
 */
public final class ImageRegionAiExchange {
    private static final int MINIMUM_PADDING_PIXELS = 16;
    private static final double PADDING_RATIO = 0.5;

    /**
     * Crops and pads each region, and writes matching regeneration instructions.
     * Padding scales with each region's own size (with a floor), so small icons
     * and large textures both get proportionate surrounding context.
     *
     * @param sourceImage source image
     * @param translations regions and their requested replacement text
     * @return one export per translation, in the same order
     * @throws OcrException on invalid regions or unreadable/unencodable image data
     */
    public List<RegionCropExport> exportRegions(
            Path sourceImage,
            List<ImageTranslation> translations) throws OcrException {
        Path source = sourceImage.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new OcrException("Source image does not exist: " + source);
        }
        try {
            BufferedImage input = ImageIO.read(source.toFile());
            if (input == null) {
                throw new OcrException("Unsupported source image: " + source);
            }
            List<RegionCropExport> exports = new ArrayList<>();
            for (ImageTranslation translation : translations) {
                exports.add(exportRegion(input, translation));
            }
            return List.copyOf(exports);
        } catch (IOException exception) {
            throw new OcrException("Could not export image regions for " + source, exception);
        }
    }

    private static RegionCropExport exportRegion(
            BufferedImage input,
            ImageTranslation translation) throws IOException, OcrException {
        OcrTextRegion region = translation.sourceRegion();
        int horizontalPadding = (int) Math.max(MINIMUM_PADDING_PIXELS, region.width() * PADDING_RATIO);
        int verticalPadding = (int) Math.max(MINIMUM_PADDING_PIXELS, region.height() * PADDING_RATIO);
        int left = Math.max(0, region.left() - horizontalPadding);
        int top = Math.max(0, region.top() - verticalPadding);
        int right = Math.min(input.getWidth(), region.left() + region.width() + horizontalPadding);
        int bottom = Math.min(input.getHeight(), region.top() + region.height() + verticalPadding);
        int width = right - left;
        int height = bottom - top;
        if (width < 1 || height < 1) {
            throw new OcrException("Image translation region is outside source bounds");
        }
        BufferedImage crop = input.getSubimage(left, top, width, height);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(crop, "png", bytes)) {
            throw new OcrException("PNG encoder is unavailable");
        }
        return new RegionCropExport(
                region,
                translation.translatedText(),
                left,
                top,
                width,
                height,
                bytes.toByteArray(),
                instructions(translation, width, height));
    }

    private static String instructions(ImageTranslation translation, int width, int height) {
        return """
                This is a cropped region from a Starsector game image, exported for \
                AI-assisted translation.%n\
                %nThe image currently shows the text "%s". Regenerate this image with \
                that text replaced by "%s", preserving the original art style, colors, \
                lighting, texture, perspective, and composition as closely as possible.%n\
                %nDo not add, remove, or alter any other part of the image. Return the \
                image at exactly %d x %d pixels.%n\
                %nReturn only the regenerated image, with no commentary.\
                """.formatted(translation.sourceRegion().text(), translation.translatedText(), width, height);
    }

    /**
     * Validates a candidate regenerated crop without compositing it.
     * Useful for immediate feedback right after the user selects a file.
     *
     * @param pngBytes candidate regenerated crop
     * @param expectedWidth required width in pixels
     * @param expectedHeight required height in pixels
     * @throws OcrException when the image is unreadable or the wrong size
     */
    public void validateRegeneratedRegionDimensions(
            byte[] pngBytes,
            int expectedWidth,
            int expectedHeight) throws OcrException {
        decodeAndValidate(pngBytes, expectedWidth, expectedHeight);
    }

    /**
     * Pastes each regenerated crop into a copy of the source image at its
     * recorded position. The source image is never modified.
     *
     * @param sourceImage source image
     * @param regions regenerated crops and their recorded positions
     * @return the composited PNG
     * @throws OcrException on invalid regions, size mismatches, or unreadable/unencodable data
     */
    public LocalizedImage compositeRegeneratedRegions(
            Path sourceImage,
            List<RegeneratedRegion> regions) throws OcrException {
        Path source = sourceImage.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new OcrException("Source image does not exist: " + source);
        }
        try {
            BufferedImage input = ImageIO.read(source.toFile());
            if (input == null) {
                throw new OcrException("Unsupported source image: " + source);
            }
            validateCompositeBounds(input, regions);
            List<BufferedImage> crops = new ArrayList<>();
            for (RegeneratedRegion region : regions) {
                crops.add(decodeAndValidate(region.pngBytes(), region.cropWidth(), region.cropHeight()));
            }
            BufferedImage output = new BufferedImage(
                    input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.drawImage(input, 0, 0, null);
                for (int index = 0; index < regions.size(); index++) {
                    RegeneratedRegion region = regions.get(index);
                    graphics.drawImage(crops.get(index), region.cropLeft(), region.cropTop(), null);
                }
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "png", bytes)) {
                throw new OcrException("PNG encoder is unavailable");
            }
            return new LocalizedImage(bytes.toByteArray());
        } catch (IOException exception) {
            throw new OcrException("Could not composite regenerated regions for " + source, exception);
        }
    }

    private static BufferedImage decodeAndValidate(
            byte[] pngBytes,
            int expectedWidth,
            int expectedHeight) throws OcrException {
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(pngBytes));
        } catch (IOException exception) {
            throw new OcrException("Could not read regenerated region image", exception);
        }
        if (decoded == null) {
            throw new OcrException("Unsupported regenerated region image");
        }
        if (decoded.getWidth() != expectedWidth || decoded.getHeight() != expectedHeight) {
            throw new OcrException(
                    "Regenerated region image is %dx%d; expected %dx%d".formatted(
                            decoded.getWidth(), decoded.getHeight(), expectedWidth, expectedHeight));
        }
        return decoded;
    }

    private static void validateCompositeBounds(
            BufferedImage image,
            List<RegeneratedRegion> regions) throws OcrException {
        List<Rectangle> bounds = new ArrayList<>();
        Rectangle imageBounds = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        for (RegeneratedRegion region : regions) {
            Rectangle rectangle = new Rectangle(
                    region.cropLeft(), region.cropTop(), region.cropWidth(), region.cropHeight());
            if (!imageBounds.contains(rectangle)) {
                throw new OcrException("Regenerated region is outside source image bounds");
            }
            if (bounds.stream().anyMatch(existing -> existing.intersects(rectangle))) {
                throw new OcrException("Regenerated regions overlap");
            }
            bounds.add(rectangle);
        }
    }
}
