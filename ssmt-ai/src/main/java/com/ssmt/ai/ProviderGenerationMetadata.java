package com.ssmt.ai;

import java.time.Instant;
import java.util.Objects;

/** Machine-provider identity retained with a generated candidate. */
public record ProviderGenerationMetadata(
        String providerId,
        String modelOrLanguagePackage,
        String providerVersion,
        Instant generatedAt,
        boolean aiRefined) {

    public ProviderGenerationMetadata {
        providerId = requireText(providerId, "providerId");
        modelOrLanguagePackage = Objects.requireNonNullElse(modelOrLanguagePackage, "");
        providerVersion = Objects.requireNonNullElse(providerVersion, "");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
