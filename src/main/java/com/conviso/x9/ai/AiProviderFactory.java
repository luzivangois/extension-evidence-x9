package com.conviso.x9.ai;

import com.conviso.x9.ai.provider.ClaudeAiProvider;
import com.conviso.x9.ai.provider.GeminiAiProvider;
import com.conviso.x9.ai.provider.OpenAiAiProvider;

import java.util.Locale;

public class AiProviderFactory {

    private final AiProvider openAi = new OpenAiAiProvider();
    private final AiProvider claude = new ClaudeAiProvider();
    private final AiProvider gemini = new GeminiAiProvider();

    public AiProvider forId(String providerId) {
        String id = providerId == null ? "" : providerId.toLowerCase(Locale.ROOT);
        if ("openai".equals(id)) {
            return openAi;
        }
        if ("claude".equals(id)) {
            return claude;
        }
        return gemini;
    }
}
