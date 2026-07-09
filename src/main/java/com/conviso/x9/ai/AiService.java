package com.conviso.x9.ai;

import com.conviso.x9.evidence.HttpEvidence;
import com.conviso.x9.model.RequirementItem;
import com.conviso.x9.model.VulnerabilityTemplateDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates AI-assisted summaries/classification on top of a provider
 * chosen at runtime by the user. Unlike the original implementation, this
 * class has no dependency on the Burp API: callers extract {@link HttpEvidence}
 * up front, which also makes every method here testable without a live Burp
 * instance.
 */
public final class AiService {

    private static final int SNIPPET_MAX_LENGTH = 1800;

    private static final String REQUIREMENT_ANALYSIS_PROMPTS_PATH = "/ai-prompts/requirement-analysis.properties";

    private final AiProviderFactory providerFactory;
    private final AiPromptConfig vulnerabilityFieldPrompts;
    private final AiPromptConfig requirementAnalysisPrompts;

    public AiService() {
        this(new AiProviderFactory(), new AiPromptConfig(), new AiPromptConfig(REQUIREMENT_ANALYSIS_PROMPTS_PATH));
    }

    public AiService(AiProviderFactory providerFactory) {
        this(providerFactory, new AiPromptConfig(), new AiPromptConfig(REQUIREMENT_ANALYSIS_PROMPTS_PATH));
    }

    public AiService(AiProviderFactory providerFactory, AiPromptConfig vulnerabilityFieldPrompts, AiPromptConfig requirementAnalysisPrompts) {
        this.providerFactory = providerFactory;
        this.vulnerabilityFieldPrompts = vulnerabilityFieldPrompts;
        this.requirementAnalysisPrompts = requirementAnalysisPrompts;
    }

    /**
     * Builds a 3-line evidence summary via AI, falling back to a static
     * templated summary when no AI key is configured or the call fails.
     */
    public String buildSummary(String aiApiKey, String providerId, String language, HttpEvidence evidence, RequirementItem requirement) {
        HttpEvidence safeEvidence = evidence == null ? HttpEvidence.EMPTY : evidence;
        String requirementId = requirement == null ? "" : safe(requirement.getId());
        String requirementTitle = requirement == null ? "" : safe(requirement.getTitle());
        String requirementDescription = requirement == null ? "" : safe(requirement.getDescription());
        String safeTitle = requirementTitle.isEmpty() ? requirementId : requirementTitle;

        if (!safe(aiApiKey).isEmpty()) {
            try {
                String aiSummary = generateSummary(aiApiKey, providerId, language, safeEvidence, requirementId, requirementTitle, requirementDescription);
                if (!safe(aiSummary).trim().isEmpty()) {
                    return aiSummary;
                }
            } catch (AiServiceException ex) {
                // Falls back to a static summary when the AI call fails.
            }
        }

        if ("en".equals(language)) {
            return "Requirement: " + safeTitle + "\nEndpoint: " + safeEvidence.getMethod() + " " + safeEvidence.getUrl()
                + "\nResult: HTTP " + safeEvidence.getStatus();
        }
        return "Requirement: " + safeTitle + "\nEndpoint: " + safeEvidence.getMethod() + " " + safeEvidence.getUrl()
            + "\nResultado: HTTP " + safeEvidence.getStatus();
    }

    /** Asks the AI to pick one requirement id out of the given catalog based on the evidence. */
    public String classifyRequirement(String aiApiKey, String providerId, HttpEvidence evidence, List<RequirementItem> catalog) throws AiServiceException {
        StringBuilder catalogText = new StringBuilder();
        for (RequirementItem requirement : catalog) {
            catalogText.append("id=").append(requirement.getId())
                .append(" | title=").append(safe(requirement.getTitle()))
                .append(" | description=").append(safe(requirement.getDescription()))
                .append("\n");
        }

        String evidenceText = evidence == null ? "" : evidence.asSearchableText();
        String userPrompt =
            "Choose exactly one requirement id from this catalog:\n" + catalogText +
            "Evidence:\n" + evidenceText +
            "\nReturn only the id value, without explanation.";

        String content = providerFactory.forId(providerId).generateContent(
            aiApiKey,
            "You classify pentest evidence into one requirement id from the provided catalog. Return only the id.",
            userPrompt, 0.0, 40
        );
        return safe(content).trim().replaceAll("[^0-9A-Za-z_-]", "");
    }

    /** Asks the AI to draft vulnerability fields (as raw JSON) from HTTP evidence and requirement context. */
    public String autocompleteVulnerability(String aiApiKey, String providerId, HttpEvidence evidence, RequirementItem requirement, String language)
        throws AiServiceException {
        HttpEvidence safeEvidence = evidence == null ? HttpEvidence.EMPTY : evidence;
        String requirementTitle = requirement == null ? "" : safe(requirement.getTitle());
        String requirementDescription = requirement == null ? "" : safe(requirement.getDescription());
        String requestSnippet = truncate(safeEvidence.getRequestSnippet());
        String responseSnippet = truncate(safeEvidence.getResponseSnippet());

        String userPrompt =
            "Language=" + ("en".equals(language) ? "English" : "Portuguese") + ". " +
            "Analyze pentest evidence and fill vulnerability fields for customer documentation. " +
            "Context requirement title=" + requirementTitle + ", description=" + requirementDescription + ". " +
            "Request method=" + safeEvidence.getMethod() + ", endpoint=" + safeEvidence.getUrl() + ", status=" + safeEvidence.getStatus() + ". " +
            "Request snippet: " + requestSnippet + " Response snippet: " + responseSnippet + ". " +
            "Also infer a category and a pattern for local classification. " +
            "Return only JSON. Severity must be one of LOW, MEDIUM, HIGH, CRITICAL.";

        return providerFactory.forId(providerId).generateContent(
            aiApiKey,
            "Return ONLY valid JSON with keys: title, severity, description, justification, evidence, category, pattern. Never include template.",
            userPrompt, 0.1, 420
        );
    }

    /**
     * Drafts one narrative field of the vulnerability creation form ("summary",
     * "impactDescription" or "stepsToReproduce") from HTTP evidence and the
     * selected template, using the prompt configured for that field in
     * {@code ai-prompts/vulnerability-fields.properties}.
     */
    public String generateVulnerabilityField(
        String field, String aiApiKey, String providerId, String language, HttpEvidence evidence, VulnerabilityTemplateDetail template
    ) throws AiServiceException {
        HttpEvidence safeEvidence = evidence == null ? HttpEvidence.EMPTY : evidence;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("method", safeEvidence.getMethod());
        placeholders.put("url", safeEvidence.getUrl());
        placeholders.put("status", safeEvidence.getStatus());
        placeholders.put("requestSnippet", truncate(safeEvidence.getRequestSnippet()));
        placeholders.put("responseSnippet", truncate(safeEvidence.getResponseSnippet()));
        placeholders.put("templateTitle", template == null ? "" : safe(template.getTitle()));
        placeholders.put("templateCategory", template == null ? "" : safe(template.getCategoryList()));
        placeholders.put("templateDescription", template == null ? "" : safe(template.getDescription()));

        String systemPrompt = vulnerabilityFieldPrompts.systemPrompt(field, language);
        String userPrompt = vulnerabilityFieldPrompts.userPrompt(field, language, placeholders);

        String content = providerFactory.forId(providerId).generateContent(aiApiKey, systemPrompt, userPrompt, 0.2, 420);
        String normalized = safe(content).trim();
        if (normalized.isEmpty()) {
            throw new AiServiceException("Empty AI field for " + field);
        }
        return normalized;
    }

    /** Throws {@link AiServiceException} unless the provider returns a non-empty response for the given key. */
    public void validateApiKey(String aiApiKey, String providerId) throws AiServiceException {
        String content = providerFactory.forId(providerId).generateContent(
            aiApiKey, "Respond with a single token.", "Reply only with the word OK.", 0.0, 16
        );
        if (safe(content).trim().isEmpty()) {
            throw new AiServiceException("Invalid response from the AI.");
        }
    }

    private String generateSummary(
        String aiApiKey,
        String providerId,
        String language,
        HttpEvidence evidence,
        String requirementId,
        String requirementTitle,
        String requirementDescription
    ) throws AiServiceException {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("requirementId", requirementId);
        placeholders.put("method", evidence.getMethod());
        placeholders.put("url", evidence.getUrl());
        placeholders.put("requirementTitle", requirementTitle);
        placeholders.put("requirementDescription", requirementDescription);
        placeholders.put("status", evidence.getStatus());

        String systemPrompt = requirementAnalysisPrompts.systemPrompt("summary", language);
        String userPrompt = requirementAnalysisPrompts.userPrompt("summary", language, placeholders);

        String content = providerFactory.forId(providerId).generateContent(aiApiKey, systemPrompt, userPrompt, 0.2, 220);
        String normalized = AiTextUtils.normalizeSummary(content);
        if (normalized.isEmpty()) {
            throw new AiServiceException("Empty AI summary");
        }
        return normalized;
    }

    private static String truncate(String value) {
        return value.length() > SNIPPET_MAX_LENGTH ? value.substring(0, SNIPPET_MAX_LENGTH) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
