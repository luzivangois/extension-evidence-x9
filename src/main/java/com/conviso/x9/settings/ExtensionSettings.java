package com.conviso.x9.settings;

import burp.IBurpExtenderCallbacks;

/**
 * Typed access to Burp's persistent extension settings, replacing scattered
 * SETTING_* string constants and ad-hoc null/blank handling at each call site.
 */
public final class ExtensionSettings {

    private static final String API_KEY = "conviso_api_key";
    private static final String SCOPE_ID = "conviso_scope_id";
    private static final String PROJECT_ID = "conviso_project_id";
    private static final String REQUIREMENT_ID = "conviso_requirement_id";
    private static final String AI_API_KEY = "conviso_ai_api_key";
    private static final String AI_PROVIDER = "conviso_ai_provider";
    private static final String X9_ITEMS = "conviso_x9_items";
    private static final String VULNERABILITIES_ITEMS = "conviso_vulnerabilities_items";
    private static final String REPORT_LANGUAGE = "conviso_report_language";

    private final IBurpExtenderCallbacks callbacks;

    public ExtensionSettings(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public String getApiKey() {
        return safe(callbacks.loadExtensionSetting(API_KEY));
    }

    public void setApiKey(String value) {
        callbacks.saveExtensionSetting(API_KEY, value);
    }

    public String getScopeId() {
        String value = safe(callbacks.loadExtensionSetting(SCOPE_ID));
        return value.isEmpty() ? "443" : value;
    }

    public void setScopeId(String value) {
        callbacks.saveExtensionSetting(SCOPE_ID, value);
    }

    public String getProjectId() {
        return safe(callbacks.loadExtensionSetting(PROJECT_ID));
    }

    public void setProjectId(String value) {
        callbacks.saveExtensionSetting(PROJECT_ID, value);
    }

    public String getRequirementId() {
        return safe(callbacks.loadExtensionSetting(REQUIREMENT_ID));
    }

    public void setRequirementId(String value) {
        callbacks.saveExtensionSetting(REQUIREMENT_ID, value);
    }

    public String getAiApiKey() {
        return safe(callbacks.loadExtensionSetting(AI_API_KEY));
    }

    public void setAiApiKey(String value) {
        callbacks.saveExtensionSetting(AI_API_KEY, value);
    }

    public String getAiProvider() {
        return safe(callbacks.loadExtensionSetting(AI_PROVIDER));
    }

    public void setAiProvider(String value) {
        callbacks.saveExtensionSetting(AI_PROVIDER, value);
    }

    public String getReportLanguage() {
        return safe(callbacks.loadExtensionSetting(REPORT_LANGUAGE));
    }

    public void setReportLanguage(String value) {
        callbacks.saveExtensionSetting(REPORT_LANGUAGE, value);
    }

    public String getX9ItemsJson() {
        return safe(callbacks.loadExtensionSetting(X9_ITEMS));
    }

    public void setX9ItemsJson(String json) {
        callbacks.saveExtensionSetting(X9_ITEMS, json);
    }

    public String getVulnerabilityItemsJson() {
        return safe(callbacks.loadExtensionSetting(VULNERABILITIES_ITEMS));
    }

    public void setVulnerabilityItemsJson(String json) {
        callbacks.saveExtensionSetting(VULNERABILITIES_ITEMS, json);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
