package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketAppInfo {
    private final long appId;
    private final String packageName;
    private final String displayName;
    private final String publisherName;
    private final String versionName;
    private final long versionCode;
    private final String iconUrl;
    private final long apkSize;
    private final double ratingScore;
    private final String changeLog;
    private final boolean ad;
    private final boolean quickApp;
    private final boolean reservationApp;
    private final String introduction;
    private final long downloadCount;
    private final long commentCount;
    private final String ageClassification;
    private final long updateTime;
    private final String registrationNumber;
    private final List<String> screenshotUrls;

    public MarketAppInfo(Builder builder) {
        appId = builder.appId;
        packageName = builder.packageName;
        displayName = builder.displayName;
        publisherName = builder.publisherName;
        versionName = builder.versionName;
        versionCode = builder.versionCode;
        iconUrl = builder.iconUrl;
        apkSize = builder.apkSize;
        ratingScore = builder.ratingScore;
        changeLog = builder.changeLog;
        ad = builder.ad;
        quickApp = builder.quickApp;
        reservationApp = builder.reservationApp;
        introduction = builder.introduction;
        downloadCount = builder.downloadCount;
        commentCount = builder.commentCount;
        ageClassification = builder.ageClassification;
        updateTime = builder.updateTime;
        registrationNumber = builder.registrationNumber;
        screenshotUrls = Collections.unmodifiableList(new ArrayList<>(builder.screenshotUrls));
    }

    public long getAppId() {
        return appId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public String getVersionName() {
        return versionName;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public long getApkSize() {
        return apkSize;
    }

    public double getRatingScore() {
        return ratingScore;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public boolean isAd() {
        return ad;
    }

    public boolean isQuickApp() { return quickApp; }
    public boolean isReservationApp() { return reservationApp; }

    public String getIntroduction() { return introduction; }
    public long getDownloadCount() { return downloadCount; }
    public long getCommentCount() { return commentCount; }
    public String getAgeClassification() { return ageClassification; }
    public long getUpdateTime() { return updateTime; }
    public String getRegistrationNumber() { return registrationNumber; }
    public List<String> getScreenshotUrls() { return screenshotUrls; }

    public static final class Builder {
        private long appId;
        private String packageName = "";
        private String displayName = "";
        private String publisherName = "";
        private String versionName = "";
        private long versionCode;
        private String iconUrl = "";
        private long apkSize;
        private double ratingScore;
        private String changeLog = "";
        private boolean ad;
        private boolean quickApp;
        private boolean reservationApp;
        private String introduction = "";
        private long downloadCount;
        private long commentCount;
        private String ageClassification = "";
        private long updateTime;
        private String registrationNumber = "";
        private List<String> screenshotUrls = Collections.emptyList();

        public Builder appId(long value) { appId = value; return this; }
        public Builder packageName(String value) { packageName = value; return this; }
        public Builder displayName(String value) { displayName = value; return this; }
        public Builder publisherName(String value) { publisherName = value; return this; }
        public Builder versionName(String value) { versionName = value; return this; }
        public Builder versionCode(long value) { versionCode = value; return this; }
        public Builder iconUrl(String value) { iconUrl = value; return this; }
        public Builder apkSize(long value) { apkSize = value; return this; }
        public Builder ratingScore(double value) { ratingScore = value; return this; }
        public Builder changeLog(String value) { changeLog = value; return this; }
        public Builder ad(boolean value) { ad = value; return this; }
        public Builder quickApp(boolean value) { quickApp = value; return this; }
        public Builder reservationApp(boolean value) { reservationApp = value; return this; }
        public Builder introduction(String value) { introduction = value; return this; }
        public Builder downloadCount(long value) { downloadCount = value; return this; }
        public Builder commentCount(long value) { commentCount = value; return this; }
        public Builder ageClassification(String value) { ageClassification = value; return this; }
        public Builder updateTime(long value) { updateTime = value; return this; }
        public Builder registrationNumber(String value) { registrationNumber = value; return this; }
        public Builder screenshotUrls(List<String> value) {
            screenshotUrls = new ArrayList<>(value);
            return this;
        }

        public MarketAppInfo build() {
            if (packageName.isEmpty()) {
                throw new IllegalStateException("packageName is required");
            }
            return new MarketAppInfo(this);
        }
    }
}
