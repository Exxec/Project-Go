package com.ssmt.ai;

/** Provider capable of describing the model/package used for a request. */
public interface AttributedTranslationProvider extends AiTranslationProvider {
    ProviderGenerationMetadata attribution(AiTranslationRequest request);
}
