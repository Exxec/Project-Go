package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderAdapterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AiTranslationRequest REQUEST =
            new AiTranslationRequest("Hello", "en", "fr", "", "");

    @Test
    void ollamaUsesNonStreamingChatAndReadsAssistantContent() throws Exception {
        CapturingTransport transport = new CapturingTransport(
                MAPPER.readTree("{\"message\":{\"content\":\"Bonjour\"}}"));
        OllamaTranslationProvider provider = new OllamaTranslationProvider(
                URI.create("http://localhost:11434"), "gemma3", transport);

        assertThat(provider.translate(REQUEST)).isEqualTo("Bonjour");
        assertThat(transport.uri.toString()).isEqualTo("http://localhost:11434/api/chat");
        assertThat(transport.body.path("stream").asBoolean()).isFalse();
        assertThat(transport.headers).isEmpty();
    }

    @Test
    void geminiUsesApiKeyHeaderAndReadsFinalTextBlock() throws Exception {
        CapturingTransport transport = new CapturingTransport(MAPPER.readTree("""
                {"steps":[{"type":"model_output","content":[{"type":"text","text":"Bonjour"}]}]}
                """));
        GeminiTranslationProvider provider = new GeminiTranslationProvider(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-3.6-flash",
                "secret",
                transport);

        assertThat(provider.translate(REQUEST)).isEqualTo("Bonjour");
        assertThat(transport.headers).containsEntry("x-goog-api-key", "secret");
        assertThat(transport.body.path("model").asText()).isEqualTo("gemini-3.6-flash");
    }

    @Test
    void openAiUsesResponsesApiAndReadsOutputTextItem() throws Exception {
        CapturingTransport transport = new CapturingTransport(MAPPER.readTree("""
                {
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": "Bonjour"}]
                  }]
                }
                """));
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                URI.create("https://api.openai.com"),
                "gpt-5.2",
                "secret",
                transport);

        assertThat(provider.translate(REQUEST)).isEqualTo("Bonjour");
        assertThat(transport.uri.toString()).isEqualTo("https://api.openai.com/v1/responses");
        assertThat(transport.headers)
                .containsEntry("Authorization", "Bearer secret");
        assertThat(transport.body.path("model").asText()).isEqualTo("gpt-5.2");
        assertThat(transport.body.path("input").asText()).contains("Hello");
    }

    @Test
    void jdkTransportPostsAndParsesUtf8Json() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/translate", exchange -> {
            byte[] response = "{\"value\":\"Réponse\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI target = URI.create(
                    "http://localhost:" + server.getAddress().getPort() + "/translate");
            JsonNode response = new JdkJsonHttpTransport(HttpClient.newHttpClient(), MAPPER)
                    .post(target, Map.of(), MAPPER.createObjectNode().put("input", "é"));
            assertThat(response.path("value").asText()).isEqualTo("Réponse");
        } finally {
            server.stop(0);
        }
    }

    private static final class CapturingTransport implements JsonHttpTransport {
        private final JsonNode response;
        private URI uri;
        private Map<String, String> headers;
        private JsonNode body;

        private CapturingTransport(JsonNode response) {
            this.response = response;
        }

        @Override
        public JsonNode post(URI target, Map<String, String> requestHeaders, JsonNode requestBody) {
            uri = target;
            headers = Map.copyOf(requestHeaders);
            body = requestBody.deepCopy();
            return response;
        }
    }
}
