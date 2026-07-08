package com.conviso.x9.model;

/** An asset (target) that belongs to a Conviso project, used to fill {@code assetId} when creating a vulnerability. */
public final class AssetItem {

    private final String id;
    private final String name;

    public AssetItem(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name == null || name.trim().isEmpty() ? id : id + " - " + name;
    }
}
