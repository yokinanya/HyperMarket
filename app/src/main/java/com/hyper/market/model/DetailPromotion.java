package com.hyper.market.model;

public final class DetailPromotion {
    private final String previewImageUrl;
    private final String expandedImageUrl;
    private final String title;
    private final String description;
    private final String category;
    private final String activityTag;
    private final String jumpUrl;

    public DetailPromotion(
            String previewImageUrl,
            String expandedImageUrl,
            String title,
            String description,
            String category,
            String activityTag,
            String jumpUrl) {
        this.previewImageUrl = previewImageUrl;
        this.expandedImageUrl = expandedImageUrl;
        this.title = title;
        this.description = description;
        this.category = category;
        this.activityTag = activityTag;
        this.jumpUrl = jumpUrl;
    }

    public String getPreviewImageUrl() { return previewImageUrl; }
    public String getExpandedImageUrl() { return expandedImageUrl; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getActivityTag() { return activityTag; }
    public String getJumpUrl() { return jumpUrl; }
}
