package com.ssmt.project;

import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.TranslationGenerationMetadata;
import java.util.Objects;
import java.util.Optional;

/** One unapproved project-entry draft returned by the configured engine. */
public record ProjectEntryTranslation(
        String translatedText,
        TranslationProvenance provenance,
        Optional<TranslationGenerationMetadata> generationMetadata,
        String backend,
        boolean unresolved) {
    public ProjectEntryTranslation {
        if (translatedText == null || translatedText.isBlank()) {
            throw new IllegalArgumentException("translatedText must not be blank");
        }
        provenance = Objects.requireNonNull(provenance, "provenance");
        generationMetadata = generationMetadata == null
                ? Optional.empty() : generationMetadata;
        backend = Objects.requireNonNullElse(backend, "unknown");
    }
}
