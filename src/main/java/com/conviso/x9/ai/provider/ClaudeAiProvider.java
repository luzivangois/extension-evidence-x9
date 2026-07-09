package com.conviso.x9.ai.provider;

import com.conviso.x9.ai.AiServiceException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClaudeAiProvider extends AbstractHttpAiProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-3-5-haiku-latest";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public String generateContent(String apiKey, String systemPrompt, String userPrompt, double temperature, int maxOutputTokens)
        throws AiServiceException {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("system", safe(systemPrompt));
        payload.addProperty("temperature", temperature);
        payload.addProperty("max_tokens", maxOutputTokens);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", safe(userPrompt));
        messages.add(user);
        payload.add("messages", messages);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", ANTHROPIC_VERSION);

        String body = postAndReadBody(API_URL, headers, payload.toString());

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new AiServiceException("Invalid response from Claude.", ex);
        }

        JsonArray content = parsed.has("content") && parsed.get("content").isJsonArray()
            ? parsed.getAsJsonArray("content") : null;
        if (content == null || content.size() == 0) {
            throw new AiServiceException("Invalid response from Claude.");
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JsonObject part = content.get(i).getAsJsonObject();
            if (part.has("text") && !part.get("text").isJsonNull()) {
                if (text.length() > 0) {
                    text.append("\n");
                }
                text.append(part.get("text").getAsString());
            }
        }
        return text.toString();
    }
}
