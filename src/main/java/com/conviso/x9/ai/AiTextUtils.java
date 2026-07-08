package com.conviso.x9.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Pure text helpers for AI responses, split out so they can be unit tested without any network call. */
public final class AiTextUtils {

    private static final int MAX_ERROR_SNIPPET_LENGTH = 260;

    private AiTextUtils() {
    }

    /**
     * Keeps at most the first 3 non-blank lines of a raw AI response and
     * strips leading numbering/bullets (e.g. "1) ", "- ").
     */
    public static String normalizeSummary(String raw) {
        if (raw == null) {
            return "";
        }
        String[] lines = raw.replace("\r", "").split("\n");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
            if (cleaned.size() == 3) {
                break;
            }
        }
        if (cleaned.isEmpty()) {
            return "";
        }

        for (int i = 0; i < cleaned.size(); i++) {
            String item = cleaned.get(i);
            item = item.replaceFirst("^\\d+[)\\.]\\s*", "");
            item = item.replaceFirst("^[-*]\\s*", "");
            cleaned.set(i, item);
        }

        return String.join("\n", cleaned);
    }

    /** Extracts a human-readable error message from a provider's JSON error body, falling back to a raw snippet. */
    public static String extractApiErrorMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "sem detalhes retornados pela API";
        }
        try {
            JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
            JsonObject error = parsed.has("error") && parsed.get("error").isJsonObject()
                ? parsed.getAsJsonObject("error") : null;
            if (error != null && error.has("message") && !error.get("message").isJsonNull()) {
                return error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // Falls back to a raw body snippet when the error body isn't valid JSON.
        }
        String compact = body.replace("\n", " ").replace("\r", " ").trim();
        return compact.length() > MAX_ERROR_SNIPPET_LENGTH ? compact.substring(0, MAX_ERROR_SNIPPET_LENGTH) + "..." : compact;
    }
}
