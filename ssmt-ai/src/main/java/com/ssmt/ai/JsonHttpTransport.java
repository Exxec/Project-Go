package com.ssmt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;

/**
 * Minimal injectable JSON HTTP boundary.
 */
@FunctionalInterface
public interface JsonHttpTransport {
    /**
     * Posts a JSON document and parses a successful JSON response.
     *
     * @param target endpoint
     * @param requestHeaders provider-specific headers
     * @param requestBody JSON body
     * @return parsed response
     * @throws AiProviderException on transport, status, or parsing failure
     */
    JsonNode post(URI target, Map<String, String> requestHeaders, JsonNode requestBody)
            throws AiProviderException;
}
