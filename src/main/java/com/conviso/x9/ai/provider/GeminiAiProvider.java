package com.conviso.x9.ai.provider;

import com.conviso.x9.ai.AiServiceException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;

public final class GeminiAiProvider extends AbstractHttpAiProvider {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MODEL = "gemini-2.0-flash";

    @Override
    public String generateContent(String apiKey, String systemPrompt, String userPrompt, double temperature, int maxOutputTokens)
        throws AiServiceException {
        JsonObject payload = new JsonObject();

        JsonArray contents = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", safe(systemPrompt) + "\n\n" + safe(userPrompt));
        userParts.add(userPart);
        user.add("parts", userParts);
        contents.add(user);
        payload.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", temperature);
        generationConfig.addProperty("maxOutputTokens", maxOutputTokens);
        payload.add("generationConfig", generationConfig);

        String url = API_BASE + MODEL + ":generateContent?key=" + apiKey;
        String body = postAndReadBody(url, Collections.emptyMap(), payload.toString());

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new AiServiceException("Invalid response from Gemini.", ex);
        }

        JsonArray candidates = parsed.has("candidates") && parsed.get("candidates").isJsonArray()
            ? parsed.getAsJsonArray("candidates") : null;
        if (candidates == null || candidates.size() == 0) {
            throw new AiServiceException("Invalid response from Gemini.");
        }
        JsonObject first = candidates.get(0).getAsJsonObject();
        JsonObject content = first.has("content") && first.get("content").isJsonObject()
            ? first.getAsJsonObject("content") : null;
        JsonArray parts = content != null && content.has("parts") && content.get("parts").isJsonArray()
            ? content.getAsJsonArray("parts") : null;
        if (parts == null || parts.size() == 0) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JsonObject part = parts.get(i).getAsJsonObject();
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
