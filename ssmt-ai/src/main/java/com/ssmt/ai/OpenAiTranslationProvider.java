package com.ssmt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Map;

/**
 * OpenAI Responses REST adapter for untrusted translation drafts.
 */
public final class OpenAiTranslationProvider implements AiTranslationProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final JsonHttpTransport transport;
    private final TranslationPromptBuilder prompts = new TranslationPromptBuilder();

    public OpenAiTranslationProvider(
            URI baseUri,
            String model,
            String apiKey,
            JsonHttpTransport transport) {
        if (baseUri == null) {
            throw new IllegalArgumentException("baseUri must not be null");
        }
        endpoint = baseUri.resolve("/v1/responses");
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
        JsonNode response = transport.post(
                endpoint,
                Map.of("Authorization", "Bearer " + apiKey),
                body);
        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        if ("output_text".equals(part.path("type").asText())) {
                            return OllamaTranslationProvider.responseText(
                                    part.path("text"), "OpenAI");
                        }
                    }
                }
            }
        }
        throw new AiProviderException("OpenAI returned no translated text");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
