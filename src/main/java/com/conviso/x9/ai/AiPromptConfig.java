package com.conviso.x9.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * Loads AI system/user prompt templates (one pair per field and language)
 * from a bundled `.properties` config file, so prompt wording can be tuned
 * without touching Java code. Used both for the vulnerability creation
 * fields (summary/impactDescription/stepsToReproduce) and for the
 * requirement (X9) evidence summary — each with its own resource file.
 */
public final class AiPromptConfig {

    private static final String DEFAULT_RESOURCE_PATH = "/ai-prompts/vulnerability-fields.properties";

    private final Properties properties;

    public AiPromptConfig() {
        this.properties = load(DEFAULT_RESOURCE_PATH);
    }

    /** Loads prompts from an arbitrary classpath resource, e.g. a different field set's config file. */
    public AiPromptConfig(String resourcePath) {
        this.properties = load(resourcePath);
    }

    public String systemPrompt(String field, String language) {
        return required(field + ".system." + langKey(language));
    }

    public String userPrompt(String field, String language, Map<String, String> placeholders) {
        return substitute(required(field + ".user." + langKey(language)), placeholders);
    }

    private static String langKey(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "pt";
    }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing AI prompt config for key: " + key);
        }
        return value;
    }

    private static String substitute(String template, Map<String, String> placeholders) {
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("{" + entry.getKey() + "}", value);
            }
        }
        return result;
    }

    private static Properties load(String resourcePath) {
        Properties properties = new Properties();
        try (InputStream stream = AiPromptConfig.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("AI prompt config resource not found: " + resourcePath);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load AI prompt config: " + resourcePath, ex);
        }
        return properties;
    }
}
