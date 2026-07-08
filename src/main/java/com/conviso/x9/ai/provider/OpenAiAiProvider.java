package com.conviso.x9.ai.provider;

import com.conviso.x9.ai.AiServiceException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiAiProvider extends AbstractHttpAiProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    @Override
    public String generateContent(String apiKey, String systemPrompt, String userPrompt, double temperature, int maxOutputTokens)
        throws AiServiceException {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("temperature", temperature);
        payload.addProperty("max_tokens", maxOutputTokens);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", safe(systemPrompt));
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", safe(userPrompt));
        messages.add(user);
        payload.add("messages", messages);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);

        String body = postAndReadBody(API_URL, headers, payload.toString());

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new AiServiceException("Resposta invalida da OpenAI.", ex);
        }

        JsonArray choices = parsed.has("choices") && parsed.get("choices").isJsonArray()
            ? parsed.getAsJsonArray("choices") : null;
        if (choices == null || choices.size() == 0) {
            throw new AiServiceException("Resposta invalida da OpenAI.");
        }
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.has("message") && first.get("message").isJsonObject()
            ? first.getAsJsonObject("message") : null;
        return message != null && message.has("content") && !message.get("content").isJsonNull()
            ? message.get("content").getAsString() : "";
    }
}
