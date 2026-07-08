package com.conviso.x9.ai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptConfigTest {

    private final AiPromptConfig vulnerabilityPrompts = new AiPromptConfig();
    private final AiPromptConfig requirementPrompts = new AiPromptConfig("/ai-prompts/requirement-analysis.properties");

    @Test
    void loadsSystemPromptForEachBundledVulnerabilityFieldAndLanguage() {
        for (String field : new String[]{"summary", "impactDescription", "stepsToReproduce"}) {
            assertTrue(!vulnerabilityPrompts.systemPrompt(field, "pt").trim().isEmpty());
            assertTrue(!vulnerabilityPrompts.systemPrompt(field, "en").trim().isEmpty());
        }
    }

    @Test
    void loadsRequirementAnalysisPromptForBothLanguages() {
        assertTrue(!requirementPrompts.systemPrompt("summary", "pt").trim().isEmpty());
        assertTrue(!requirementPrompts.systemPrompt("summary", "en").trim().isEmpty());
    }

    @Test
    void defaultsToPortugueseForUnknownLanguage() {
        assertEquals(vulnerabilityPrompts.systemPrompt("summary", "pt"), vulnerabilityPrompts.systemPrompt("summary", "xx"));
    }

    @Test
    void substitutesPlaceholdersInUserPrompt() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("method", "POST");
        placeholders.put("url", "https://example.test/login");
        placeholders.put("status", "200");
        placeholders.put("requestSnippet", "POST /login");
        placeholders.put("responseSnippet", "200 OK");
        placeholders.put("templateTitle", "Credenciais Expostas");
        placeholders.put("templateCategory", "CWE-798");
        placeholders.put("templateDescription", "desc");

        String userPrompt = vulnerabilityPrompts.userPrompt("summary", "pt", placeholders);

        assertTrue(userPrompt.contains("POST"));
        assertTrue(userPrompt.contains("https://example.test/login"));
        assertTrue(userPrompt.contains("Credenciais Expostas"));
        assertTrue(!userPrompt.contains("{method}"));
    }

    @Test
    void substitutesPlaceholdersInRequirementAnalysisUserPrompt() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("requirementId", "42");
        placeholders.put("method", "GET");
        placeholders.put("url", "https://example.test/api");
        placeholders.put("requirementTitle", "Broken Access Control");
        placeholders.put("requirementDescription", "desc");
        placeholders.put("status", "200");

        String userPrompt = requirementPrompts.userPrompt("summary", "en", placeholders);

        assertTrue(userPrompt.contains("42"));
        assertTrue(userPrompt.contains("Broken Access Control"));
        assertTrue(!userPrompt.contains("{requirementId}"));
    }

    @Test
    void throwsForUnknownField() {
        assertThrows(IllegalStateException.class, () -> vulnerabilityPrompts.systemPrompt("doesNotExist", "pt"));
    }
}
