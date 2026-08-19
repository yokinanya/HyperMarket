package com.hyper.market.model;

public final class DetailVideo {
    private final String videoUrl;
    private final String coverUrl;
    private final String title;

    public DetailVideo(String videoUrl, String coverUrl, String title) {
        this.videoUrl = videoUrl;
        this.coverUrl = coverUrl;
        this.title = title;
    }

    public String getVideoUrl() { return videoUrl; }
    public String getCoverUrl() { return coverUrl; }
    public String getTitle() { return title; }
}
