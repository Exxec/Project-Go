package com.ssmt.ocr;

/**
 * Explicit advanced typography and panel effects.
 *
 * @param baseStyle compatible base color and font style
 * @param horizontalPadding horizontal text padding
 * @param verticalPadding vertical text padding
 * @param horizontalAlignment horizontal text alignment
 * @param verticalAlignment vertical text alignment
 * @param cornerRadius rounded background arc size, zero for square
 * @param outlineArgb outline color
 * @param outlineWidth outline width, zero to disable
 * @param shadowArgb shadow color
 * @param shadowOffsetX shadow horizontal offset
 * @param shadowOffsetY shadow vertical offset
 */
public record AdvancedImageRenderStyle(
        ImageRenderStyle baseStyle,
        int horizontalPadding,
        int verticalPadding,
        HorizontalAlignment horizontalAlignment,
        VerticalAlignment verticalAlignment,
        int cornerRadius,
        int outlineArgb,
        int outlineWidth,
        int shadowArgb,
        int shadowOffsetX,
        int shadowOffsetY) {

    public AdvancedImageRenderStyle {
        if (baseStyle == null || horizontalAlignment == null || verticalAlignment == null) {
            throw new IllegalArgumentException("Advanced style values must not be null");
        }
        if (horizontalPadding < 0 || verticalPadding < 0 || cornerRadius < 0) {
            throw new IllegalArgumentException("Padding and corner radius must not be negative");
        }
        if (outlineWidth < 0 || outlineWidth > 8) {
            throw new IllegalArgumentException("Outline width must be between zero and eight");
        }
        if (Math.abs(shadowOffsetX) > 32 || Math.abs(shadowOffsetY) > 32) {
            throw new IllegalArgumentException("Shadow offsets must be within 32 pixels");
        }
    }

    /**
     * Preserves the original centered square-panel rendering behavior.
     *
     * @param style compatible base style
     * @return advanced style with every additive effect disabled
     */
    public static AdvancedImageRenderStyle defaults(ImageRenderStyle style) {
        return new AdvancedImageRenderStyle(
                style,
                0,
                0,
                HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER,
                0,
                0,
                0,
                0,
                0,
                0);
    }

    /**
     * Horizontal placement inside the padded region.
     */
    public enum HorizontalAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    /**
     * Vertical placement inside the padded region.
     */
    public enum VerticalAlignment {
        TOP,
        CENTER,
        BOTTOM
    }
}
