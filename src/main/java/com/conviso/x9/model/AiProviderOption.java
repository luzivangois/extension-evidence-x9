package com.conviso.x9.model;

public final class AiProviderOption {

    private final String id;
    private final String label;

    public AiProviderOption(String id, String label) {
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
