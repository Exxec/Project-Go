package com.ssmt.ocr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Deterministically renders translated text into explicit image regions.
 */
public final class ImageLocalizer {
    /**
     * Renders translations into an in-memory source copy and encodes PNG.
     *
     * @param sourceImage source image
     * @param translations explicit regions and text
     * @param style render style
     * @return localized PNG
     * @throws OcrException on invalid regions, unreadable input, or encoding failure
     */
    public LocalizedImage render(
            Path sourceImage,
            List<ImageTranslation> translations,
            ImageRenderStyle style) throws OcrException {
        return render(sourceImage, translations, AdvancedImageRenderStyle.defaults(style));
    }

    /**
     * Renders translations with explicit advanced panel and typography effects.
     *
     * @param sourceImage source image
     * @param translations explicit regions and text
     * @param style advanced render style
     * @return localized PNG
     * @throws OcrException on invalid regions, unreadable input, or fitting failure
     */
    public LocalizedImage render(
            Path sourceImage,
            List<ImageTranslation> translations,
            AdvancedImageRenderStyle style) throws OcrException {
        if (style == null) {
            throw new IllegalArgumentException("style must not be null");
        }
        Path source = sourceImage.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new OcrException("Source image does not exist: " + source);
        }
        List<ImageTranslation> ordered = new ArrayList<>(List.copyOf(translations));
        ordered.sort(Comparator
                .comparingInt((ImageTranslation item) -> item.sourceRegion().top())
                .thenComparingInt(item -> item.sourceRegion().left()));
        try {
            BufferedImage input = ImageIO.read(source.toFile());
            if (input == null) {
                throw new OcrException("Unsupported source image: " + source);
            }
            validateRegions(input, ordered);
            BufferedImage output =
                    new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.drawImage(input, 0, 0, null);
                graphics.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                for (ImageTranslation translation : ordered) {
                    render(graphics, translation, style);
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
            throw new OcrException("Could not localize image " + source, exception);
        }
    }

    private static void validateRegions(
            BufferedImage image,
            List<ImageTranslation> translations) throws OcrException {
        List<Rectangle> regions = new ArrayList<>();
        Rectangle bounds = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        for (ImageTranslation translation : translations) {
            OcrTextRegion region = translation.sourceRegion();
            Rectangle rectangle =
                    new Rectangle(region.left(), region.top(), region.width(), region.height());
            if (!bounds.contains(rectangle)) {
                throw new OcrException("Image translation region is outside source bounds");
            }
            if (regions.stream().anyMatch(existing -> existing.intersects(rectangle))) {
                throw new OcrException("Image translation regions overlap");
            }
            regions.add(rectangle);
        }
    }

    private static void render(
            Graphics2D graphics,
            ImageTranslation translation,
            AdvancedImageRenderStyle style) throws OcrException {
        OcrTextRegion region = translation.sourceRegion();
        ImageRenderStyle base = style.baseStyle();
        graphics.setColor(new Color(base.backgroundArgb(), true));
        if (style.cornerRadius() == 0) {
            graphics.fillRect(region.left(), region.top(), region.width(), region.height());
        } else {
            int arc = Math.min(style.cornerRadius(), Math.min(region.width(), region.height()));
            graphics.fillRoundRect(
                    region.left(), region.top(), region.width(), region.height(), arc, arc);
        }
        Font font = fittingFont(graphics, translation.translatedText(), region, style);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        Rectangle content = contentBounds(region, style);
        int textWidth = metrics.stringWidth(translation.translatedText());
        int x = switch (style.horizontalAlignment()) {
            case LEFT -> content.x;
            case CENTER -> content.x + (content.width - textWidth) / 2;
            case RIGHT -> content.x + content.width - textWidth;
        };
        int y = switch (style.verticalAlignment()) {
            case TOP -> content.y + metrics.getAscent();
            case CENTER -> content.y
                    + (content.height - metrics.getHeight()) / 2
                    + metrics.getAscent();
            case BOTTOM -> content.y + content.height - metrics.getDescent();
        };
        if (style.shadowOffsetX() != 0 || style.shadowOffsetY() != 0) {
            graphics.setColor(new Color(style.shadowArgb(), true));
            graphics.drawString(
                    translation.translatedText(),
                    x + style.shadowOffsetX(),
                    y + style.shadowOffsetY());
        }
        Shape glyphs = font.createGlyphVector(
                        graphics.getFontRenderContext(),
                        translation.translatedText())
                .getOutline(x, y);
        if (style.outlineWidth() > 0) {
            graphics.setColor(new Color(style.outlineArgb(), true));
            graphics.setStroke(new BasicStroke(style.outlineWidth()));
            graphics.draw(glyphs);
        }
        graphics.setColor(new Color(base.textArgb(), true));
        graphics.fill(glyphs);
    }

    private static Font fittingFont(
            Graphics2D graphics,
            String text,
            OcrTextRegion region,
            AdvancedImageRenderStyle style) throws OcrException {
        ImageRenderStyle base = style.baseStyle();
        Rectangle content = contentBounds(region, style);
        for (int size = base.maximumFontSize(); size >= base.minimumFontSize(); size--) {
            Font candidate = new Font(base.fontFamily(), Font.PLAIN, size);
            FontMetrics metrics = graphics.getFontMetrics(candidate);
            if (metrics.stringWidth(text) <= content.width
                    && metrics.getHeight() <= content.height) {
                return candidate;
            }
        }
        throw new OcrException("Translated image text does not fit configured region");
    }

    private static Rectangle contentBounds(
            OcrTextRegion region,
            AdvancedImageRenderStyle style) throws OcrException {
        int left = style.horizontalPadding()
                + style.outlineWidth()
                + Math.max(0, -style.shadowOffsetX());
        int right = style.horizontalPadding()
                + style.outlineWidth()
                + Math.max(0, style.shadowOffsetX());
        int top = style.verticalPadding()
                + style.outlineWidth()
                + Math.max(0, -style.shadowOffsetY());
        int bottom = style.verticalPadding()
                + style.outlineWidth()
                + Math.max(0, style.shadowOffsetY());
        int width = region.width() - left - right;
        int height = region.height() - top - bottom;
        if (width < 1 || height < 1) {
            throw new OcrException("Advanced image style leaves no text content area");
        }
        return new Rectangle(
                region.left() + left,
                region.top() + top,
                width,
                height);
    }
}
