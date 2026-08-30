package com.ssmt.ocr;

import java.util.Objects;

/**
 * One exported, padded crop of a source image region, ready to hand to an
 * external AI image tool alongside its generated instructions text.
 *
 * @param sourceRegion original detected/marked region
 * @param translatedText requested replacement text
 * @param cropLeft padded crop left pixel within the full source image
 * @param cropTop padded crop top pixel within the full source image
 * @param cropWidth padded crop width in pixels
 * @param cropHeight padded crop height in pixels
 * @param pngBytes the cropped region, encoded as PNG
 * @param instructions human/AI-readable regeneration instructions
 */
public record RegionCropExport(
        OcrTextRegion sourceRegion,
        String translatedText,
        int cropLeft,
        int cropTop,
        int cropWidth,
        int cropHeight,
        byte[] pngBytes,
        String instructions) {

    public RegionCropExport {
        Objects.requireNonNull(sourceRegion, "sourceRegion must not be null");
        if (translatedText == null || translatedText.isBlank()) {
            throw new IllegalArgumentException("translatedText must not be blank");
        }
        if (cropLeft < 0 || cropTop < 0 || cropWidth < 1 || cropHeight < 1) {
            throw new IllegalArgumentException("crop geometry must be positive");
        }
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IllegalArgumentException("pngBytes must not be empty");
        }
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("instructions must not be blank");
        }
        pngBytes = pngBytes.clone();
    }

    @Override
    public byte[] pngBytes() {
        return pngBytes.clone();
    }
}
