package com.ssmt.ocr;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Injectable external-process boundary.
 */
@FunctionalInterface
public interface OcrProcess {
    /**
     * Executes a bounded command.
     *
     * @param command exact argument list
     * @param timeout maximum runtime
     * @return captured result
     * @throws IOException on launch or stream failure
     * @throws InterruptedException when interrupted
     */
    ProcessResult execute(List<String> command, Duration timeout)
            throws IOException, InterruptedException;
}
