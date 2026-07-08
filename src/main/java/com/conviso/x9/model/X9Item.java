package com.conviso.x9.model;

public final class X9Item {

    private String projectId = "";
    private String requirementId = "";
    private String entryId = "";
    private String title = "";
    private String summary = "";
    private String state = "DRAFT";
    private String sentAt = "";
    private String approvedBy = "";

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(String requirementId) {
        this.requirementId = requirementId;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSentAt() {
        return sentAt;
    }

    public void setSentAt(String sentAt) {
        this.sentAt = sentAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    @Override
    public String toString() {
        String safeTitle = title == null || title.trim().isEmpty() ? "(sem titulo)" : title;
        String safeState = state == null || state.trim().isEmpty() ? "DRAFT" : state;
        if ("SENT".equals(safeState)) {
            String stamp = sentAt == null || sentAt.trim().isEmpty() ? "-" : sentAt;
            return "[" + safeState + "] Req " + requirementId + " - " + safeTitle + " (" + stamp + ")";
        }
        return "[" + safeState + "] Req " + requirementId + " - " + safeTitle;
    }
}
