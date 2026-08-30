package com.ssmt.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class BoundedLocalTranslationProcess implements LocalTranslationProcess {
    private static final int MAX_CAPTURE_BYTES = 1_048_576;

    @Override
    public LocalProcessResult execute(
            List<String> command,
            String input,
            Duration timeout,
            Map<String, String> environment)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(List.copyOf(command));
        builder.environment().putAll(Map.copyOf(environment));
        Process child = builder.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> output = executor.submit(() -> readBounded(child.getInputStream()));
            Future<String> error = executor.submit(() -> readBounded(child.getErrorStream()));
            try (var stdin = child.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!child.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                child.destroyForcibly();
                throw new IOException("local translation process timed out after " + timeout);
            }
            return new LocalProcessResult(child.exitValue(), result(output), result(error));
        } catch (InterruptedException exception) {
            child.destroyForcibly();
            throw exception;
        }
    }

    private static String readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) >= 0) {
            if (bytes.size() + count > MAX_CAPTURE_BYTES) {
                throw new IOException("local translation output exceeded 1 MiB");
            }
            bytes.write(buffer, 0, count);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String result(Future<String> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            throw new IOException("could not capture local translation output", exception.getCause());
        }
    }
}
