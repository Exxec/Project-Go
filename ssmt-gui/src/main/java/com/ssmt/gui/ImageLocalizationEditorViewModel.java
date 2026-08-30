package com.ssmt.gui;

import com.ssmt.ocr.ImageLocalizer;
import com.ssmt.ocr.ImageRegionAiExchange;
import com.ssmt.ocr.ImageRenderStyle;
import com.ssmt.ocr.ImageTranslation;
import com.ssmt.ocr.LocalizedImage;
import com.ssmt.ocr.OcrException;
import com.ssmt.ocr.OcrTextRegion;
import com.ssmt.ocr.RegeneratedRegion;
import com.ssmt.ocr.RegionCropExport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit-region visual image-localization editor state.
 */
public final class ImageLocalizationEditorViewModel {
    private final ImageLocalizer localizer = new ImageLocalizer();
    private final ImageRegionAiExchange aiExchange = new ImageRegionAiExchange();
    private final List<ImageTranslation> translations = new ArrayList<>();
    private final Map<Integer, RegeneratedRegion> regeneratedByIndex = new LinkedHashMap<>();
    private List<RegionCropExport> lastExports = List.of();
    private Path sourceImage;

    /**
     * Loads detected regions without modifying the image.
     *
     * @param source source image
     * @param regions detected OCR regions
     */
    public void load(Path source, List<OcrTextRegion> regions) {
        sourceImage = source.toAbsolutePath().normalize();
        translations.clear();
        lastExports = List.of();
        regeneratedByIndex.clear();
        regions.stream()
                .sorted(Comparator.comparingInt(OcrTextRegion::top)
                        .thenComparingInt(OcrTextRegion::left))
                .map(region -> new ImageTranslation(region, region.text()))
                .forEach(translations::add);
    }

    /**
     * Replaces the current region set for the already-loaded source image
     * (e.g. after adding a manual region or re-running text detection),
     * preserving hand-typed translations and imported AI-regenerated crops
     * for any region whose geometry is unchanged. A region is identified by
     * its exact pixel geometry, not its position in the list, since
     * detection can reorder or renumber regions.
     *
     * @param regions the full replacement region set
     * @throws IllegalStateException when no source image is loaded yet
     */
    public void reload(List<OcrTextRegion> regions) {
        ensureSourceLoaded();
        Map<RegionKey, String> preservedText = new LinkedHashMap<>();
        Map<RegionKey, RegeneratedRegion> preservedRegenerated = new LinkedHashMap<>();
        for (int index = 0; index < translations.size(); index++) {
            ImageTranslation translation = translations.get(index);
            RegionKey key = RegionKey.of(translation.sourceRegion());
            preservedText.put(key, translation.translatedText());
            RegeneratedRegion regenerated = regeneratedByIndex.get(index);
            if (regenerated != null) {
                preservedRegenerated.put(key, regenerated);
            }
        }

        translations.clear();
        lastExports = List.of();
        regeneratedByIndex.clear();
        List<OcrTextRegion> sorted = regions.stream()
                .sorted(Comparator.comparingInt(OcrTextRegion::top)
                        .thenComparingInt(OcrTextRegion::left))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            OcrTextRegion region = sorted.get(index);
            RegionKey key = RegionKey.of(region);
            String text = preservedText.getOrDefault(key, region.text());
            translations.add(new ImageTranslation(region, text));
            RegeneratedRegion regenerated = preservedRegenerated.get(key);
            if (regenerated != null) {
                regeneratedByIndex.put(index, regenerated);
            }
        }
    }

    private record RegionKey(int left, int top, int width, int height) {
        static RegionKey of(OcrTextRegion region) {
            return new RegionKey(region.left(), region.top(), region.width(), region.height());
        }
    }

    /**
     * Replaces translated text for one region.
     *
     * @param index deterministic region index
     * @param translatedText replacement text
     */
    public void edit(int index, String translatedText) {
        ImageTranslation previous = translations.get(index);
        translations.set(index, new ImageTranslation(
                previous.sourceRegion(), translatedText));
        // The requested replacement text changed, so any prior AI export/import
        // for this region no longer matches what should be regenerated.
        lastExports = List.of();
        regeneratedByIndex.remove(index);
    }

    /**
     * @return immutable current translations
     */
    public List<ImageTranslation> translations() {
        return List.copyOf(translations);
    }

    /**
     * Renders and writes a user-owned PNG without changing the source.
     *
     * @param destination output PNG
     * @param style deterministic render style
     * @throws OcrException on render failure
     */
    public void render(Path destination, ImageRenderStyle style) throws OcrException {
        ensureSourceLoaded();
        LocalizedImage image = localizer.render(sourceImage, translations, style);
        writeFile(destination, image);
    }

    /**
     * Crops and pads every current region for handoff to an external AI image
     * tool. SSMT never calls an image generation service itself.
     *
     * @return one export per region, in editor order
     * @throws OcrException when there is no source image, no regions, or the
     *     source image cannot be read
     */
    public List<RegionCropExport> exportForImageAi() throws OcrException {
        ensureSourceLoaded();
        if (translations.isEmpty()) {
            throw new OcrException("No regions to export");
        }
        lastExports = aiExchange.exportRegions(sourceImage, translations);
        regeneratedByIndex.clear();
        return List.copyOf(lastExports);
    }

    /**
     * Validates and stores one region's AI-regenerated crop for later
     * compositing. Never overwrites the source image.
     *
     * @param index region index from the last {@link #exportForImageAi()} call
     * @param pngBytes candidate regenerated crop
     * @throws OcrException when the region was never exported, or the image is
     *     unreadable or the wrong size
     */
    public void importRegeneratedRegion(int index, byte[] pngBytes) throws OcrException {
        if (index < 0 || index >= lastExports.size()) {
            throw new OcrException("Export this region for AI regeneration first");
        }
        RegionCropExport export = lastExports.get(index);
        aiExchange.validateRegeneratedRegionDimensions(
                pngBytes, export.cropWidth(), export.cropHeight());
        regeneratedByIndex.put(index, new RegeneratedRegion(
                export.cropLeft(), export.cropTop(), export.cropWidth(), export.cropHeight(),
                pngBytes));
    }

    /**
     * @return number of regions with a validated regenerated crop imported
     */
    public int regeneratedRegionCount() {
        return regeneratedByIndex.size();
    }

    /**
     * Composites every imported regenerated crop into a copy of the source
     * image and writes a user-owned PNG without changing the source.
     *
     * @param destination output PNG
     * @throws OcrException when there is no source image, nothing has been
     *     imported yet, or compositing fails
     */
    public void renderWithRegeneratedRegions(Path destination) throws OcrException {
        ensureSourceLoaded();
        if (regeneratedByIndex.isEmpty()) {
            throw new OcrException("No regenerated regions have been imported yet");
        }
        LocalizedImage image = aiExchange.compositeRegeneratedRegions(
                sourceImage, List.copyOf(regeneratedByIndex.values()));
        writeFile(destination, image);
    }

    private void ensureSourceLoaded() {
        if (sourceImage == null) {
            throw new IllegalStateException("No source image is loaded");
        }
    }

    private static void writeFile(Path destination, LocalizedImage image) throws OcrException {
        try {
            Files.write(destination, image.pngBytes());
        } catch (IOException exception) {
            throw new OcrException("Could not write localized image", exception);
        }
    }
}
