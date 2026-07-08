package com.conviso.x9.ai;

/** A chat-completion-style AI backend (Gemini, OpenAI, Claude, ...). */
public interface AiProvider {

    String generateContent(String apiKey, String systemPrompt, String userPrompt, double temperature, int maxOutputTokens)
        throws AiServiceException;
}
