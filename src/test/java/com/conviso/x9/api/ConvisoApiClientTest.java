package com.conviso.x9.api;

import com.conviso.x9.model.VulnerabilityRecord;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConvisoApiClientTest {

    private final ConvisoApiClient client = new ConvisoApiClient();

    @Test
    void parsesFullIssuePayload() {
        JsonObject asset = new JsonObject();
        asset.addProperty("name", "api.example.com/login");

        JsonObject issue = new JsonObject();
        issue.addProperty("id", "42");
        issue.addProperty("title", "SQL Injection");
        issue.addProperty("severity", "HIGH");
        issue.addProperty("type", "Injection / SQL Injection");
        issue.addProperty("description", "Unsanitized input");
        issue.addProperty("justification", "Confirmed via error-based injection");
        issue.addProperty("evidence", "payload reflected");
        issue.addProperty("status", "OPEN");
        issue.addProperty("createdAt", "2026-01-01T00:00:00Z");
        issue.add("asset", asset);

        VulnerabilityRecord record = client.parseVulnerabilityRecord(issue, "PRJ-1");

        assertEquals("42", record.getId());
        assertEquals("SQL Injection", record.getTitle());
        assertEquals("HIGH", record.getSeverity());
        assertEquals("PRJ-1", record.getProjectId());
        assertEquals("Injection / SQL Injection", record.getTemplate());
        assertEquals("Unsanitized input", record.getDescription());
        assertEquals("Confirmed via error-based injection", record.getJustification());
        assertEquals("payload reflected", record.getEvidence());
        assertEquals("api.example.com/login", record.getEndpoint());
        assertEquals("OPEN", record.getStatus());
        assertEquals("2026-01-01T00:00:00Z", record.getCreatedAt());
    }

    @Test
    void fallsBackToTechnicalDetailWhenJustificationMissing() {
        JsonObject issue = new JsonObject();
        issue.addProperty("id", "7");
        issue.addProperty("technicalDetail", "detail from technicalDetail field");

        VulnerabilityRecord record = client.parseVulnerabilityRecord(issue, "PRJ-1");

        assertEquals("detail from technicalDetail field", record.getJustification());
    }

    @Test
    void derivesEndpointFromIdWhenAssetIsMissing() {
        JsonObject issue = new JsonObject();
        issue.addProperty("id", "99");

        VulnerabilityRecord record = client.parseVulnerabilityRecord(issue, "PRJ-1");

        assertEquals("issue/99", record.getEndpoint());
    }

    @Test
    void handlesMissingOptionalFieldsAsEmptyStrings() {
        JsonObject issue = new JsonObject();

        VulnerabilityRecord record = client.parseVulnerabilityRecord(issue, "PRJ-1");

        assertEquals("", record.getId());
        assertEquals("", record.getTitle());
        assertEquals("", record.getEndpoint());
    }
}
