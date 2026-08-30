package com.ssmt.ai;

/**
 * Optional draft-translation provider.
 */
@FunctionalInterface
public interface AiTranslationProvider {
    /**
     * Produces an untrusted translation draft.
     *
     * @param request translation input
     * @return translated text only
     * @throws AiProviderException on provider failure
     */
    String translate(AiTranslationRequest request) throws AiProviderException;
}
