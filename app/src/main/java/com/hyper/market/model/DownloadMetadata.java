package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DownloadMetadata {
    private final MarketAppInfo app;
    private final List<ApkArtifact> artifacts;
    private final long totalSize;
    private final String versionName;
    private final long versionCode;

    public DownloadMetadata(
            MarketAppInfo app,
            List<ApkArtifact> artifacts,
            long totalSize,
            String versionName,
            long versionCode) {
        this.app = app;
        this.artifacts = Collections.unmodifiableList(new ArrayList<>(artifacts));
        this.totalSize = totalSize;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    public MarketAppInfo getApp() { return app; }
    public List<ApkArtifact> getArtifacts() { return Collections.unmodifiableList(artifacts); }
    public long getTotalSize() { return totalSize; }
    public String getVersionName() { return versionName; }
    public long getVersionCode() { return versionCode; }
}
