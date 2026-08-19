package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TodayArticle {
    private final String resourceId;
    private final String title;
    private final String headerImageUrl;
    private final String bodyHtml;
    private final List<String> imageUrls;
    private final List<MarketAppInfo> apps;

    public TodayArticle(
            String resourceId,
            String title,
            String headerImageUrl,
            String bodyHtml,
            List<String> imageUrls,
            List<MarketAppInfo> apps) {
        this.resourceId = resourceId;
        this.title = title;
        this.headerImageUrl = headerImageUrl;
        this.bodyHtml = bodyHtml;
        this.imageUrls = Collections.unmodifiableList(new ArrayList<>(imageUrls));
        this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getHeaderImageUrl() {
        return headerImageUrl;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public List<MarketAppInfo> getApps() {
        return apps;
    }
}
