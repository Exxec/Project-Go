package com.ssmt.gui;

import com.ssmt.ai.AiProviderSettings;
import com.ssmt.ai.AiProviderType;
import java.net.URI;
import java.util.Optional;

/**
 * Secret-free AI provider settings editor state.
 */
public final class AiProviderSettingsViewModel {
    private AiProviderSettings settings;

    /**
     * Validates and stores only non-secret configuration.
     *
     * @param type provider type
     * @param baseUri provider base URI
     * @param model model identifier
     * @param credentialEnvironmentVariable credential environment-variable name
     */
    public void update(
            AiProviderType type,
            String baseUri,
            String model,
            String credentialEnvironmentVariable) {
        Optional<String> credential = credentialEnvironmentVariable == null
                        || credentialEnvironmentVariable.isBlank()
                ? Optional.empty()
                : Optional.of(credentialEnvironmentVariable);
        settings = new AiProviderSettings(type, URI.create(baseUri), model, credential);
    }

    /**
     * @return current validated non-secret settings
     */
    public Optional<AiProviderSettings> settings() {
        return Optional.ofNullable(settings);
    }
}
