package com.ssmt.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@FunctionalInterface
interface LocalTranslationProcess {
    LocalProcessResult execute(
            List<String> command,
            String input,
            Duration timeout,
            Map<String, String> environment)
            throws IOException, InterruptedException;
}
