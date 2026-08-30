package com.ssmt.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Java HTTP client implementation of the JSON transport boundary.
 */
public final class JdkJsonHttpTransport implements JsonHttpTransport {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private final HttpClient client;
    private final ObjectMapper mapper;

    public JdkJsonHttpTransport(HttpClient client, ObjectMapper mapper) {
        if (client == null || mapper == null) {
            throw new IllegalArgumentException("client and mapper must not be null");
        }
        this.client = client;
        this.mapper = mapper.copy();
    }

    @Override
    public JsonNode post(URI target, Map<String, String> requestHeaders, JsonNode requestBody)
            throws AiProviderException {
        try {
            byte[] body = mapper.writeValueAsBytes(requestBody);
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            requestHeaders.forEach(builder::header);
            HttpResponse<byte[]> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(
                        "Provider returned HTTP status " + response.statusCode());
            }
            return mapper.readTree(new String(response.body(), StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("Provider returned malformed JSON", exception);
        } catch (IOException exception) {
            throw new AiProviderException("Provider request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Provider request was interrupted", exception);
        }
    }
}
