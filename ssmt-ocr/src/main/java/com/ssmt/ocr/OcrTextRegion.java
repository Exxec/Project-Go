package com.ssmt.ocr;

/**
 * Text detected within one image line.
 *
 * @param text detected text
 * @param left left pixel
 * @param top top pixel
 * @param width width in pixels
 * @param height height in pixels
 * @param confidence minimum word confidence from zero to one hundred
 */
public record OcrTextRegion(
        String text,
        int left,
        int top,
        int width,
        int height,
        double confidence) {

    /**
     * Validates region geometry and content.
     */
    public OcrTextRegion {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (left < 0 || top < 0 || width < 1 || height < 1) {
            throw new IllegalArgumentException("region geometry must be positive");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 100.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 100");
        }
    }
}
