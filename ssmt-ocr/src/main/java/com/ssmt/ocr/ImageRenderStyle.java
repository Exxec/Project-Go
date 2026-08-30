package com.ssmt.ocr;

/**
 * Explicit deterministic image translation styling.
 *
 * @param backgroundArgb replacement background color
 * @param textArgb translated text color
 * @param fontFamily logical or installed font family
 * @param minimumFontSize smallest permitted point size
 * @param maximumFontSize largest permitted point size
 */
public record ImageRenderStyle(
        int backgroundArgb,
        int textArgb,
        String fontFamily,
        int minimumFontSize,
        int maximumFontSize) {

    /**
     * Validates font configuration.
     */
    public ImageRenderStyle {
        if (fontFamily == null || fontFamily.isBlank()) {
            throw new IllegalArgumentException("fontFamily must not be blank");
        }
        if (minimumFontSize < 1 || maximumFontSize < minimumFontSize) {
            throw new IllegalArgumentException("Invalid font size bounds");
        }
    }
}
