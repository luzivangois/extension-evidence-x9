package com.conviso.x9.api;

import com.conviso.x9.http.HttpJsonResponse;
import com.conviso.x9.http.JsonHttpClient;
import com.conviso.x9.http.MultipartHttpClient;
import com.conviso.x9.model.VulnerabilityDraft;
import com.conviso.x9.model.VulnerabilityRecord;
import com.conviso.x9.model.VulnerabilityTemplateDetail;
import com.conviso.x9.model.VulnerabilityTemplateSummary;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GraphQL client for the Conviso Platform API. */
public final class ConvisoApiClient {

    private static final String API_URL = "https://api.app.convisoappsec.com/graphql";

    private final Gson gson = new Gson();

    public JsonArray fetchProjects(String apiKey, String scopeId, int limit) throws ConvisoApiException {
        JsonObject variables = new JsonObject();
        variables.addProperty("page", 1);
        variables.addProperty("limit", limit);
        variables.addProperty("sortBy", "name");
        variables.addProperty("descending", true);

        JsonObject params = new JsonObject();
        params.addProperty("search", "");
        if (!scopeId.trim().isEmpty()) {
            try {
                params.addProperty("scopeIdEq", Integer.parseInt(scopeId.trim()));
            } catch (NumberFormatException ignored) {
                params.addProperty("scopeIdEq", scopeId.trim());
            }
        }
        params.add("teams", new JsonArray());
        params.add("analystsEmailIn", new JsonArray());
        params.add("tags", new JsonArray());
        params.add("environmentCompromised", null);

        JsonArray statusLabels = new JsonArray();
        statusLabels.add("PLANNED");
        statusLabels.add("ANALYSIS");
        params.add("projectStatusLabelIn", statusLabels);
        params.add("projectTypeLabelIn", new JsonArray());

        variables.add("params", params);

        JsonObject payload = new JsonObject();
        payload.addProperty("operationName", "projects");
        payload.add("variables", variables);
        payload.addProperty("query", GraphQLQueries.PROJECTS_QUERY);

        JsonObject response = graphqlRequest(apiKey, payload);
        return response.getAsJsonObject("data").getAsJsonObject("projects").getAsJsonArray("collection");
    }

    public JsonArray fetchRequirements(String apiKey, String projectId) throws ConvisoApiException {
        JsonObject variables = new JsonObject();
        variables.addProperty("sortBy", "SORT");
        variables.addProperty("descending", false);

        JsonObject params = new JsonObject();
        params.addProperty("projectId", projectId);
        params.addProperty("title", "");
        variables.add("params", params);

        JsonObject pagination = new JsonObject();
        pagination.addProperty("page", 1);
        pagination.addProperty("perPage", 100);
        variables.add("pagination", pagination);

        JsonObject payload = new JsonObject();
        payload.addProperty("operationName", "Activities");
        payload.add("variables", variables);
        payload.addProperty("query", GraphQLQueries.ACTIVITIES_QUERY);

        JsonObject response = graphqlRequest(apiKey, payload);
        return response.getAsJsonObject("data").getAsJsonObject("activities").getAsJsonArray("collection");
    }

    public JsonArray fetchActivities(String apiKey, String projectId, String requirementId) throws ConvisoApiException {
        JsonObject variables = new JsonObject();
        variables.addProperty("sortBy", "SORT");
        variables.addProperty("descending", false);

        JsonObject params = new JsonObject();
        params.addProperty("projectId", projectId);
        params.addProperty("projectRequirementId", requirementId);
        params.addProperty("title", "");
        variables.add("params", params);

        JsonObject pagination = new JsonObject();
        pagination.addProperty("page", 1);
        pagination.addProperty("perPage", 20);
        variables.add("pagination", pagination);

        JsonObject payload = new JsonObject();
        payload.addProperty("operationName", "Activities");
        payload.add("variables", variables);
        payload.addProperty("query", GraphQLQueries.ACTIVITIES_QUERY);

        JsonObject response = graphqlRequest(apiKey, payload);
        return response.getAsJsonObject("data").getAsJsonObject("activities").getAsJsonArray("collection");
    }

    public JsonArray fetchProjectVulnerabilities(String apiKey, String projectId, String companyId) throws ConvisoApiException {
        if (companyId == null || companyId.trim().isEmpty()) {
            throw new ConvisoApiException("Enter the Company ID in Settings > Configuration.");
        }

        JsonObject pagination = new JsonObject();
        pagination.addProperty("page", 1);
        pagination.addProperty("perPage", 100);

        JsonObject filters = new JsonObject();
        JsonArray projectIds = new JsonArray();
        projectIds.add(projectId);
        filters.add("projectIds", projectIds);

        JsonArray sortOptions = new JsonArray();
        JsonObject riskSort = new JsonObject();
        riskSort.addProperty("sortBy", "RISK_SCORE");
        riskSort.addProperty("order", "DESC");
        sortOptions.add(riskSort);

        JsonObject severitySort = new JsonObject();
        severitySort.addProperty("sortBy", "SEVERITY");
        severitySort.addProperty("order", "DESC");
        sortOptions.add(severitySort);

        JsonObject variables = new JsonObject();
        variables.add("pagination", pagination);
        variables.add("filters", filters);
        if (companyId.trim().matches("\\d+")) {
            variables.addProperty("companyId", Integer.parseInt(companyId.trim()));
        } else {
            variables.addProperty("companyId", companyId.trim());
        }
        variables.add("sortOptions", sortOptions);

        JsonObject payload = new JsonObject();
        payload.addProperty("operationName", "Issues");
        payload.add("variables", variables);
        payload.addProperty("query", GraphQLQueries.ISSUES_QUERY);

        JsonObject response = graphqlRequest(apiKey, payload);
        JsonObject data = response.getAsJsonObject("data");
        if (data == null || !data.has("issues") || data.get("issues").isJsonNull()) {
            return new JsonArray();
        }
        JsonObject issues = data.getAsJsonObject("issues");
        if (issues == null || !issues.has("collection") || !issues.get("collection").isJsonArray()) {
            return new JsonArray();
        }
        return issues.getAsJsonArray("collection");
    }

    /** Lists the company's full vulnerability template catalog (paginated internally, no artificial cap). */
    public List<VulnerabilityTemplateSummary> fetchVulnerabilityTemplates(String apiKey, String companyId, String search) throws ConvisoApiException {
        List<VulnerabilityTemplateSummary> templates = new ArrayList<>();
        int page = 1;
        int totalPages = 1;
        int pageSize = 200;

        do {
            JsonObject variables = new JsonObject();
            addCompanyId(variables, companyId);
            variables.addProperty("page", page);
            variables.addProperty("limit", pageSize);
            variables.addProperty("search", search == null ? "" : search);
            variables.addProperty("sortBy", "title");
            variables.addProperty("descending", false);

            JsonObject payload = new JsonObject();
            payload.addProperty("operationName", "vulnerabilitiesTemplatesByCompanyId");
            payload.add("variables", variables);
            payload.addProperty("query", GraphQLQueries.VULNERABILITY_TEMPLATES_QUERY);

            JsonObject response = graphqlRequest(apiKey, payload);
            JsonObject data = response.getAsJsonObject("data");
            JsonObject result = data == null ? null : data.getAsJsonObject("vulnerabilitiesTemplatesByCompanyId");
            if (result == null) {
                break;
            }

            JsonArray collection = result.has("collection") && result.get("collection").isJsonArray()
                ? result.getAsJsonArray("collection") : new JsonArray();
            for (int i = 0; i < collection.size(); i++) {
                JsonObject obj = collection.get(i).getAsJsonObject();
                templates.add(new VulnerabilityTemplateSummary(
                    getString(obj, "id"), getString(obj, "title"), getString(obj, "description"), getString(obj, "criticity")
                ));
            }

            JsonObject metadata = result.has("metadata") && result.get("metadata").isJsonObject() ? result.getAsJsonObject("metadata") : null;
            totalPages = metadata != null && metadata.has("totalPages") && !metadata.get("totalPages").isJsonNull()
                ? metadata.get("totalPages").getAsInt() : page;
            page++;
        } while (page <= totalPages);

        return templates;
    }

    public VulnerabilityTemplateDetail fetchVulnerabilityTemplateDetail(String apiKey, String templateId) throws ConvisoApiException {
        JsonObject variables = new JsonObject();
        variables.addProperty("id", templateId);

        JsonObject payload = new JsonObject();
        payload.addProperty("operationName", "vulnerabilityTemplate");
        payload.add("variables", variables);
        payload.addProperty("query", GraphQLQueries.VULNERABILITY_TEMPLATE_BY_ID_QUERY);

        JsonObject response = graphqlRequest(apiKey, payload);
        JsonObject data = response.getAsJsonObject("data");
        JsonObject obj = data == null ? null : data.getAsJsonObject("vulnerabilityTemplate");
        if (obj == null) {
            throw new ConvisoApiException("Vulnerability template not found: " + templateId);
        }

        VulnerabilityTemplateDetail detail = new VulnerabilityTemplateDetail();
        detail.setId(templateId);
        detail.setTitle(getString(obj, "title"));
        detail.setCategoryList(getString(obj, "categoryList"));
        detail.setPatternList(getString(obj, "patternList"));
        detail.setCriticity(getString(obj, "criticity"));
        detail.setProbability(getString(obj, "probability"));
        detail.setImpact(getString(obj, "impact"));
        detail.setImpactResume(getString(obj, "impactResume"));
        detail.setDescription(getString(obj, "description"));
        detail.setReference(getString(obj, "reference"));
        detail.setSolution(getString(obj, "solution"));
        detail.setNotification(obj.has("notification") && !obj.get("notification").isJsonNull() && obj.get("notification").getAsBoolean());
        return detail;
    }

    /** Creates the vulnerability and returns the created issue's id. */
    public String createWebVulnerability(String apiKey, VulnerabilityDraft draft) throws ConvisoApiException {
        JsonObject input = new JsonObject();
        input.addProperty("title", draft.getTitle());
        input.addProperty("description", draft.getDescription());
        input.addProperty("solution", draft.getSolution());
        input.addProperty("category", draft.getCategory());

        JsonArray patterns = new JsonArray();
        for (String pattern : draft.getPatterns()) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                patterns.add(pattern.trim());
            }
        }
        input.add("patterns", patterns);

        input.addProperty("reference", draft.getReference());
        input.addProperty("impactLevel", draft.getImpactLevel());
        input.addProperty("probabilityLevel", draft.getProbabilityLevel());
        input.addProperty("summary", draft.getSummary());
        input.addProperty("impactDescription", draft.getImpactDescription());
        input.addProperty("stepsToReproduce", draft.getStepsToReproduce());
        input.addProperty("compromisedEnvironment", draft.isCompromisedEnvironment());
        addIntOrString(input, "assetId", draft.getAssetId());
        addIntOrString(input, "projectId", draft.getProjectId());
        input.addProperty("severity", draft.getSeverity());
        input.addProperty("status", draft.getStatus());
        input.addProperty("request", draft.getRequest());
        input.addProperty("response", draft.getResponse());
        input.addProperty("method", draft.getMethod());
        input.addProperty("parameters", draft.getParameters());
        input.addProperty("url", draft.getUrl());
        input.addProperty("scheme", draft.getScheme());
        input.addProperty("port", draft.getPort());

        JsonObject variables = new JsonObject();
        variables.add("input", input);

        JsonObject payload = new JsonObject();
        payload.addProperty("operationName", "CreateWebVulnerability");
        payload.add("variables", variables);
        payload.addProperty("query", GraphQLQueries.CREATE_WEB_VULNERABILITY_MUTATION);

        JsonObject response = graphqlRequest(apiKey, payload);
        JsonObject data = response.getAsJsonObject("data");
        JsonObject created = data == null ? null : data.getAsJsonObject("createWebVulnerability");
        JsonObject issue = created == null ? null : created.getAsJsonObject("issue");
        String issueId = issue == null ? "" : getString(issue, "id");
        if (issueId.isEmpty()) {
            throw new ConvisoApiException("The Conviso Platform response did not include the created vulnerability's id.");
        }
        return issueId;
    }

    /**
     * Uploads one evidence attachment to an already-created vulnerability (issue).
     * Only {@code issueId} is sent (never {@code projectId}): the platform rejects
     * an attachment linked to both an issue and a project at the same time.
     */
    public void uploadAttachment(String apiKey, String companyId, String issueId, String fileName, String contentType, byte[] fileBytes)
        throws ConvisoApiException {
        JsonObject variables = new JsonObject();
        addCompanyId(variables, companyId);
        variables.add("archive", JsonNull.INSTANCE);
        addIntOrString(variables, "issueId", issueId);

        JsonObject operations = new JsonObject();
        operations.addProperty("operationName", "CreateAttachment");
        operations.add("variables", variables);
        operations.addProperty("query", GraphQLQueries.CREATE_ATTACHMENT_MUTATION);

        JsonObject map = new JsonObject();
        JsonArray archivePath = new JsonArray();
        archivePath.add("variables.archive");
        map.add("1", archivePath);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X_API_KEY", apiKey);
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Origin", "https://app.convisoappsec.com");
        headers.put("Referer", "https://app.convisoappsec.com/");
        // multipart/form-data is a "simple request" by CORS rules, so Apollo Server's
        // CSRF-prevention plugin blocks it unless one of these headers is present
        // (this is exactly what apollo-upload-client injects on every upload).
        headers.put("Apollo-Require-Preflight", "true");
        headers.put("x-apollo-operation-name", "CreateAttachment");

        HttpJsonResponse response;
        try {
            response = MultipartHttpClient.postGraphqlFileUpload(
                API_URL, headers, gson.toJson(operations), gson.toJson(map), "1", fileName, contentType, fileBytes
            );
        } catch (IOException ex) {
            throw new ConvisoApiException("Network failure while attaching evidence to the Conviso Platform: " + ex.getMessage(), ex);
        }

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(response.getBody()).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new ConvisoApiException("Invalid response from the Conviso Platform while attaching evidence (HTTP " + response.getStatus() + ")", ex);
        }
        if (parsed.has("errors") && !parsed.get("errors").isJsonNull()) {
            throw new ConvisoApiException(parsed.get("errors").toString());
        }
        if (!response.isSuccess()) {
            throw new ConvisoApiException("HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }

    private void addCompanyId(JsonObject variables, String companyId) {
        addIntOrString(variables, "companyId", companyId);
    }

    private void addIntOrString(JsonObject target, String key, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.matches("\\d+")) {
            target.addProperty(key, Integer.parseInt(trimmed));
        } else {
            target.addProperty(key, trimmed);
        }
    }

    /**
     * Sets a requirement (activity) to DONE with a comment, optionally attaching one evidence file.
     * Pass {@code evidencePng} as {@code null} to leave archives empty (e.g. when there's no live
     * Burp evidence available, such as the "Follow requirement" flow from an existing vulnerability).
     */
    public void markRequirementDone(String apiKey, String requirementId, String comment, byte[] evidencePng) throws ConvisoApiException {
        if (evidencePng == null) {
            JsonObject input = buildActivityStatusInput(requirementId, "DONE", comment);
            input.add("archives", new JsonArray());

            JsonObject variables = new JsonObject();
            variables.add("input", input);

            JsonObject payload = new JsonObject();
            payload.addProperty("operationName", "UpdateActivityStatus");
            payload.add("variables", variables);
            payload.addProperty("query", GraphQLQueries.UPDATE_ACTIVITY_STATUS_MUTATION);

            JsonObject response = graphqlRequest(apiKey, payload);
            checkActivityMutationErrors(response, "updateActivityStatus");
            return;
        }

        JsonObject input = buildActivityStatusInput(requirementId, "DONE", comment);
        uploadActivityAttachmentMutation(apiKey, "UpdateActivityStatus", GraphQLQueries.UPDATE_ACTIVITY_STATUS_MUTATION, "updateActivityStatus", input, "evidence.png", "image/png", evidencePng);
    }

    /** Attaches one evidence file to a requirement (activity) with a comment, without changing its status. */
    public void addRequirementAttachment(String apiKey, String requirementId, String comment, byte[] evidencePng) throws ConvisoApiException {
        JsonObject input = new JsonObject();
        addIntOrString(input, "id", requirementId);
        input.addProperty("reason", comment);

        uploadActivityAttachmentMutation(apiKey, "AddActivityAttachment", GraphQLQueries.ADD_ACTIVITY_ATTACHMENT_MUTATION, "addActivityAttachment", input, "evidence.png", "image/png", evidencePng);
    }

    private JsonObject buildActivityStatusInput(String requirementId, String status, String comment) {
        JsonObject input = new JsonObject();
        addIntOrString(input, "id", requirementId);
        input.addProperty("status", status);
        input.addProperty("reason", comment);
        return input;
    }

    private void uploadActivityAttachmentMutation(
        String apiKey, String operationName, String query, String mutationField, JsonObject input, String fileName, String contentType, byte[] fileBytes
    ) throws ConvisoApiException {
        JsonArray archives = new JsonArray();
        archives.add(JsonNull.INSTANCE);
        input.add("archives", archives);

        JsonObject variables = new JsonObject();
        variables.add("input", input);

        JsonObject operations = new JsonObject();
        operations.addProperty("operationName", operationName);
        operations.add("variables", variables);
        operations.addProperty("query", query);

        JsonObject map = new JsonObject();
        JsonArray archivePath = new JsonArray();
        archivePath.add("variables.input.archives.0");
        map.add("1", archivePath);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X_API_KEY", apiKey);
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Origin", "https://app.convisoappsec.com");
        headers.put("Referer", "https://app.convisoappsec.com/");
        headers.put("Apollo-Require-Preflight", "true");
        headers.put("x-apollo-operation-name", operationName);

        HttpJsonResponse response;
        try {
            response = MultipartHttpClient.postGraphqlFileUpload(
                API_URL, headers, gson.toJson(operations), gson.toJson(map), "1", fileName, contentType, fileBytes
            );
        } catch (IOException ex) {
            throw new ConvisoApiException("Network failure while sending evidence to the requirement: " + ex.getMessage(), ex);
        }

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(response.getBody()).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new ConvisoApiException("Invalid response from the Conviso Platform (HTTP " + response.getStatus() + ")", ex);
        }
        if (parsed.has("errors") && !parsed.get("errors").isJsonNull()) {
            throw new ConvisoApiException(parsed.get("errors").toString());
        }
        if (!response.isSuccess()) {
            throw new ConvisoApiException("HTTP " + response.getStatus() + ": " + response.getBody());
        }
        checkActivityMutationErrors(parsed, mutationField);
    }

    private void checkActivityMutationErrors(JsonObject response, String mutationField) throws ConvisoApiException {
        JsonObject data = response.getAsJsonObject("data");
        JsonObject mutationResult = data == null ? null : data.getAsJsonObject(mutationField);
        if (mutationResult != null && mutationResult.has("errors") && mutationResult.get("errors").isJsonArray()
            && mutationResult.getAsJsonArray("errors").size() > 0) {
            throw new ConvisoApiException(mutationResult.getAsJsonArray("errors").toString());
        }
    }

    public VulnerabilityRecord parseVulnerabilityRecord(JsonObject obj, String projectId) {
        VulnerabilityRecord record = new VulnerabilityRecord();
        record.setId(getString(obj, "id"));
        record.setTitle(getString(obj, "title"));
        record.setSeverity(getString(obj, "severity"));
        record.setProjectId(projectId);
        record.setTemplate(getString(obj, "type"));
        record.setDescription(getString(obj, "description"));

        String justification = getString(obj, "justification");
        if (justification.isEmpty()) {
            justification = getString(obj, "technicalDetail");
        }
        record.setJustification(justification);
        record.setEvidence(getString(obj, "evidence"));

        String endpoint = "";
        if (obj != null && obj.has("asset") && obj.get("asset").isJsonObject()) {
            endpoint = getString(obj.getAsJsonObject("asset"), "name");
        }
        if (endpoint.isEmpty() && !record.getId().isEmpty()) {
            endpoint = "issue/" + record.getId();
        }
        record.setEndpoint(endpoint);

        record.setMethod(getString(obj, "status"));
        record.setCreatedAt(getString(obj, "createdAt"));
        return record;
    }

    private JsonObject graphqlRequest(String apiKey, JsonObject payload) throws ConvisoApiException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X_API_KEY", apiKey);
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Origin", "https://app.convisoappsec.com");
        headers.put("Referer", "https://app.convisoappsec.com/");

        HttpJsonResponse response;
        try {
            response = JsonHttpClient.post(API_URL, headers, gson.toJson(payload));
        } catch (IOException ex) {
            throw new ConvisoApiException("Network failure while calling the Conviso Platform: " + ex.getMessage(), ex);
        }

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(response.getBody()).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new ConvisoApiException("Invalid response from the Conviso Platform (HTTP " + response.getStatus() + ")", ex);
        }

        if (parsed.has("errors") && !parsed.get("errors").isJsonNull()) {
            throw new ConvisoApiException(parsed.get("errors").toString());
        }
        if (!response.isSuccess()) {
            throw new ConvisoApiException("HTTP " + response.getStatus() + ": " + response.getBody());
        }
        return parsed;
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }
}
