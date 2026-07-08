package com.conviso.x9.model;

import java.util.Collections;
import java.util.List;

public final class ProjectItem {

    private final String id;
    private final String display;
    private final String label;
    private final String pid;
    private final List<AssetItem> assets;

    public ProjectItem(String id, String display, String label, String pid) {
        this(id, display, label, pid, Collections.emptyList());
    }

    public ProjectItem(String id, String display, String label, String pid, List<AssetItem> assets) {
        this.id = id;
        this.display = display;
        this.label = label;
        this.pid = pid;
        this.assets = assets == null ? Collections.emptyList() : assets;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getLabel() {
        return label;
    }

    public String getPid() {
        return pid;
    }

    public List<AssetItem> getAssets() {
        return assets;
    }

    @Override
    public String toString() {
        return display;
    }
}
