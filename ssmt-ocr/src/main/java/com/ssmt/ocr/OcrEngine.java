package com.ssmt.ocr;

import java.nio.file.Path;
import java.util.List;

/**
 * Optional image-text extraction boundary.
 */
@FunctionalInterface
public interface OcrEngine {
    /**
     * Extracts text regions without modifying the image.
     *
     * @param sourceImage source image
     * @return regions in deterministic reading order
     * @throws OcrException on process or parsing failure
     */
    List<OcrTextRegion> extract(Path sourceImage) throws OcrException;
}
