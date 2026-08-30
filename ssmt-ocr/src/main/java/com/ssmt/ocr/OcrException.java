package com.ssmt.ocr;

import com.ssmt.core.exception.SsmtException;

/**
 * Reports OCR process and output failures.
 */
public final class OcrException extends SsmtException {
    private static final long serialVersionUID = 1L;

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
