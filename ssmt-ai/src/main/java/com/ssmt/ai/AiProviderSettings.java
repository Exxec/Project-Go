package com.ssmt.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.function.Function;

/**
 * Non-secret provider configuration.
 *
 * @param type provider type
 * @param baseUri provider base URI
 * @param model provider model
 * @param credentialEnvironmentVariable environment-variable name, never its value
 */
public record AiProviderSettings(
        AiProviderType type,
        URI baseUri,
        String model,
        Optional<String> credentialEnvironmentVariable) {

    public AiProviderSettings {
        if (type == null || baseUri == null || model == null || model.isBlank()) {
            throw new IllegalArgumentException("Provider settings must be complete");
        }
        credentialEnvironmentVariable =
                credentialEnvironmentVariable == null
                        ? Optional.empty()
                        : credentialEnvironmentVariable;
        credentialEnvironmentVariable.ifPresent(AiProviderSettings::validateVariable);
        if (type != AiProviderType.OLLAMA && credentialEnvironmentVariable.isEmpty()) {
            throw new IllegalArgumentException(
                    "Remote providers require a credential environment-variable name");
        }
    }

    /**
     * Resolves credentials only while constructing the provider.
     *
     * @param environment environment lookup
     * @return configured provider
     */
    public AiTranslationProvider create(Function<String, String> environment) {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        JsonHttpTransport transport = new JdkJsonHttpTransport(
                HttpClient.newHttpClient(), new ObjectMapper());
        return switch (type) {
            case OLLAMA -> new OllamaTranslationProvider(baseUri, model, transport);
            case OPENAI -> new OpenAiTranslationProvider(
                    baseUri, model, credential(environment), transport);
            case GEMINI -> new GeminiTranslationProvider(
                    baseUri, model, credential(environment), transport);
        };
    }

    private String credential(Function<String, String> environment) {
        String name = credentialEnvironmentVariable.orElseThrow();
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Credential environment variable is missing: " + name);
        }
        return value;
    }

    private static void validateVariable(String value) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid environment-variable name");
        }
    }
}
