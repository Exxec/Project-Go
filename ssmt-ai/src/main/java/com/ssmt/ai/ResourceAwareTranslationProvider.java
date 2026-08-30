package com.ssmt.ai;

/** Translation provider that reports supported resource capabilities. */
public interface ResourceAwareTranslationProvider extends AttributedTranslationProvider {
    TranslationProviderCapabilities capabilities();
}
