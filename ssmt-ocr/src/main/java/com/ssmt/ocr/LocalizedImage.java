package com.ssmt.ocr;

import java.util.Arrays;

/**
 * Defensively owned localized PNG bytes.
 */
public final class LocalizedImage {
    private final byte[] pngBytes;

    public LocalizedImage(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IllegalArgumentException("pngBytes must not be empty");
        }
        this.pngBytes = pngBytes.clone();
    }

    public byte[] pngBytes() {
        return pngBytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalizedImage image
                && Arrays.equals(pngBytes, image.pngBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pngBytes);
    }
}
