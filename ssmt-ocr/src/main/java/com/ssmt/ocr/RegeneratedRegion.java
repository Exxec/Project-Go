package com.ssmt.ocr;

/**
 * One AI-regenerated crop, ready to be pasted back at its recorded position.
 *
 * @param cropLeft padded crop left pixel within the full source image
 * @param cropTop padded crop top pixel within the full source image
 * @param cropWidth expected crop width in pixels
 * @param cropHeight expected crop height in pixels
 * @param pngBytes the regenerated crop, encoded as PNG
 */
public record RegeneratedRegion(int cropLeft, int cropTop, int cropWidth, int cropHeight, byte[] pngBytes) {

    public RegeneratedRegion {
        if (cropLeft < 0 || cropTop < 0 || cropWidth < 1 || cropHeight < 1) {
            throw new IllegalArgumentException("crop geometry must be positive");
        }
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IllegalArgumentException("pngBytes must not be empty");
        }
        pngBytes = pngBytes.clone();
    }

    @Override
    public byte[] pngBytes() {
        return pngBytes.clone();
    }
}
