package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TodayFeaturedItem {
    private final String resourceId;
    private final String title;
    private final String summary;
    private final String coverImageUrl;
    private final String clickUrl;
    private final MarketAppInfo app;
    private final List<MarketAppInfo> apps;
    private final boolean goldenAward;

    public TodayFeaturedItem(
            String resourceId,
            String title,
            String summary,
            String coverImageUrl,
            String clickUrl,
            MarketAppInfo app,
            List<MarketAppInfo> apps,
            boolean goldenAward) {
        this.resourceId = resourceId;
        this.title = title;
        this.summary = summary;
        this.coverImageUrl = coverImageUrl;
        this.clickUrl = clickUrl;
        this.app = app;
        this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        this.goldenAward = goldenAward;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    public MarketAppInfo getApp() {
        return app;
    }

    public List<MarketAppInfo> getApps() {
        return apps;
    }

    public boolean isGoldenAward() {
        return goldenAward;
    }
}
