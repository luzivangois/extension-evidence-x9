package com.conviso.x9.model;

/** One entry of the X9 tab's "Requirement" filter combo (applies to both Pendentes and Enviados). */
public final class RequirementFilterOption {

    private final String id;
    private final String label;

    public RequirementFilterOption(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
