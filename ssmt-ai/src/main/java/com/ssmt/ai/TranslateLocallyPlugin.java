package com.ssmt.ai;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Offline TranslateLocally CLI adapter using a user-installed model. */
public final class TranslateLocallyPlugin implements ResourceAwareTranslationProvider {
    /** Current Chinese-to-English model default; installation remains user-managed. */
    public static final String DEFAULT_ZH_EN_MODEL = "Helsinki-NLP/opus-mt-zh-en";

    private final Path executable;
    private final String model;
    private final LocalTranslationProcess process;
    private final Duration timeout;

    public TranslateLocallyPlugin(Path executable, String model) {
        this(executable, model, new BoundedLocalTranslationProcess(), Duration.ofMinutes(2));
    }

    TranslateLocallyPlugin(
            Path executable, String model, LocalTranslationProcess process, Duration timeout) {
        this.executable = Objects.requireNonNull(executable, "executable");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.model = model;
        this.process = Objects.requireNonNull(process, "process");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
    }

    @Override
    public String translate(AiTranslationRequest request) throws AiProviderException {
        try {
            LocalProcessResult result = process.execute(
                    List.of(executable.toString(), "-m", model), request.sourceText(), timeout,
                    Map.of());
            if (result.exitCode() != 0) {
                throw new AiProviderException("TranslateLocally exited with exit code "
                        + result.exitCode() + ": " + result.standardError().strip());
            }
            return result.standardOutput().strip();
        } catch (IOException exception) {
            throw new AiProviderException("Could not execute TranslateLocally", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("TranslateLocally was interrupted", exception);
        }
    }

    @Override
    public TranslationProviderCapabilities capabilities() {
        return new TranslationProviderCapabilities(false, false, false);
    }

    @Override
    public ProviderGenerationMetadata attribution(AiTranslationRequest request) {
        return new ProviderGenerationMetadata(
                "translate-locally", model, "", Instant.now(), false);
    }
}
