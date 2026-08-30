package com.ssmt.ocr;

/**
 * Replacement text for one detected image region.
 *
 * @param sourceRegion source OCR region
 * @param translatedText text to render
 */
public record ImageTranslation(OcrTextRegion sourceRegion, String translatedText) {
    /**
     * Validates translated content.
     */
    public ImageTranslation {
        if (sourceRegion == null) {
            throw new IllegalArgumentException("sourceRegion must not be null");
        }
        if (translatedText == null || translatedText.isBlank()) {
            throw new IllegalArgumentException("translatedText must not be blank");
        }
    }
}
