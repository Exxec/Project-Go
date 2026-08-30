package com.ssmt.ai;

/**
 * Builds deterministic context-aware translation prompts.
 */
public final class TranslationPromptBuilder {
    /**
     * Builds a plain-text provider prompt.
     *
     * @param request translation input
     * @return deterministic prompt
     */
    public String build(AiTranslationRequest request) {
        if (!request.preparedPrompt().isBlank()) {
            return request.preparedPrompt();
        }
        StringBuilder prompt = new StringBuilder()
                .append("Translate the source text from ")
                .append(request.sourceLanguage())
                .append(" to ")
                .append(request.targetLanguage())
                .append(".\n")
                .append("Return only the translated text. Preserve placeholders and $tokens exactly.\n");
        if (!request.context().isBlank()) {
            prompt.append("Context:\n").append(request.context()).append('\n');
        }
        if (!request.glossary().isBlank()) {
            prompt.append("Glossary:\n").append(request.glossary()).append('\n');
        }
        return prompt.append("Source text:\n").append(request.sourceText()).toString();
    }
}
