package com.ssmt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Map;

/**
 * Gemini Interactions REST adapter.
 */
public final class GeminiTranslationProvider implements AiTranslationProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final JsonHttpTransport transport;
    private final TranslationPromptBuilder prompts = new TranslationPromptBuilder();

    public GeminiTranslationProvider(
            URI baseUri,
            String model,
            String apiKey,
            JsonHttpTransport transport) {
        this.endpoint = baseUri.resolve("/v1beta/interactions");
        this.model = requireText(model, "model");
        this.apiKey = requireText(apiKey, "apiKey");
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.transport = transport;
    }

    @Override
    public String translate(AiTranslationRequest request) throws AiProviderException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("input", prompts.build(request));
        JsonNode response =
                transport.post(endpoint, Map.of("x-goog-api-key", apiKey), body);
        JsonNode steps = response.path("steps");
        if (steps.isArray()) {
            for (int stepIndex = steps.size() - 1; stepIndex >= 0; stepIndex--) {
                JsonNode content = steps.get(stepIndex).path("content");
                if (content.isArray()) {
                    for (int index = content.size() - 1; index >= 0; index--) {
                        JsonNode item = content.get(index);
                        if ("text".equals(item.path("type").asText())) {
                            return OllamaTranslationProvider.responseText(
                                    item.path("text"), "Gemini");
                        }
                    }
                }
            }
        }
        throw new AiProviderException("Gemini returned no translated text");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
