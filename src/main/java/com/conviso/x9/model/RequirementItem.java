package com.conviso.x9.model;

public final class RequirementItem {

    private final String id;
    private final String status;
    private final String title;
    private final String description;

    public RequirementItem(String id, String status, String title, String description) {
        this.id = id;
        this.status = status;
        this.title = title;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return id + " - [" + status + "] " + title;
    }
}
