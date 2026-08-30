package com.ssmt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Map;

/**
 * Non-streaming Ollama chat adapter.
 */
public final class OllamaTranslationProvider implements AiTranslationProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final URI endpoint;
    private final String model;
    private final JsonHttpTransport transport;
    private final TranslationPromptBuilder prompts = new TranslationPromptBuilder();

    public OllamaTranslationProvider(URI baseUri, String model, JsonHttpTransport transport) {
        this.endpoint = baseUri.resolve("/api/chat");
        this.model = requireText(model, "model");
        this.transport = requireTransport(transport);
    }

    @Override
    public String translate(AiTranslationRequest request) throws AiProviderException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompts.build(request));
        JsonNode response = transport.post(endpoint, Map.of(), body);
        return responseText(response.at("/message/content"), "Ollama");
    }

    static String responseText(JsonNode node, String provider) throws AiProviderException {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new AiProviderException(provider + " returned no translated text");
        }
        return node.textValue();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static JsonHttpTransport requireTransport(JsonHttpTransport value) {
        if (value == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        return value;
    }
}
