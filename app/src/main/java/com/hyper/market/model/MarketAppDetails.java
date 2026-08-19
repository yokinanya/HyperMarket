package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketAppDetails {
    private final MarketAppInfo app;
    private final String briefShow;
    private final String category;
    private final String privacyUrl;
    private final List<DetailComment> comments;
    private final List<MarketAppInfo> sameDeveloperApps;
    private final List<DetailPromotion> promotions;
    private final List<DetailVideo> videos;

    public MarketAppDetails(
            MarketAppInfo app,
            String briefShow,
            String category,
            String privacyUrl,
            List<DetailComment> comments,
            List<MarketAppInfo> sameDeveloperApps,
            List<DetailPromotion> promotions) {
        this(app, briefShow, category, privacyUrl, comments, sameDeveloperApps, promotions,
                Collections.emptyList());
    }

    public MarketAppDetails(
            MarketAppInfo app,
            String briefShow,
            String category,
            String privacyUrl,
            List<DetailComment> comments,
            List<MarketAppInfo> sameDeveloperApps,
            List<DetailPromotion> promotions,
            List<DetailVideo> videos) {
        this.app = app;
        this.briefShow = briefShow;
        this.category = category;
        this.privacyUrl = privacyUrl;
        this.comments = immutable(comments);
        this.sameDeveloperApps = immutable(sameDeveloperApps);
        this.promotions = immutable(promotions);
        this.videos = immutable(videos);
    }

    public MarketAppInfo getApp() { return app; }
    public String getBriefShow() { return briefShow; }
    public String getCategory() { return category; }
    public String getPrivacyUrl() { return privacyUrl; }
    public List<DetailComment> getComments() { return comments; }
    public List<MarketAppInfo> getSameDeveloperApps() { return sameDeveloperApps; }
    public List<DetailPromotion> getPromotions() { return promotions; }
    public List<DetailVideo> getVideos() { return videos; }

    private <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
