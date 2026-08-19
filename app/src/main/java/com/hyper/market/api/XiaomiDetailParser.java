package com.hyper.market.api;

import com.hyper.market.model.MarketAppDetails;
import com.hyper.market.model.MarketAppInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class XiaomiDetailParser {
    private XiaomiDetailParser() { }

    static MarketAppDetails parseDetails(JSONObject response, MarketAppInfo base) {
        JSONObject data = response.optJSONObject("data");
        JSONObject briefData = firstObject(response, data, "detailTabBriefShow");
        List<String> screenshots = new ArrayList<>(base.getScreenshotUrls());
        appendScreenshots(response.optJSONArray("detailVideoAndScreenshotList"), screenshots);
        if (data != null) {
            appendScreenshots(data.optJSONArray("detailVideoAndScreenshotList"), screenshots);
        }
        String brief = firstText(briefData, "briefShow", "introduction", "description");
        if (brief.isEmpty()) {
            brief = firstText(data, "briefShow", "introduction", "description");
        }
        String category = firstText(response, "category", "categoryName");
        if (category.isEmpty()) {
            category = firstText(data, "category", "categoryName", "level1CategoryName");
        }
        String privacyUrl = privacyUrl(response, data, briefData);
        long commentCount = firstLong(
                response, data, "ratingTotalCount", "commentCount", "totalCommentCount", "commentNum");
        MarketAppInfo app = copyApp(base)
                .introduction(brief.isEmpty() ? base.getIntroduction() : brief)
                .commentCount(commentCount > 0 ? commentCount : base.getCommentCount())
                .screenshotUrls(screenshots)
                .build();
        return new MarketAppDetails(
                app,
                brief,
                category,
                privacyUrl,
                parseComments(response, data, briefData),
                parseSameDeveloperApps(response, data),
                parsePromotions(response, data),
                XiaomiDetailExtrasParser.parseVideos(response, data));
    }

    static MarketAppInfo parse(JSONObject response, MarketAppInfo base) {
        return parseDetails(response, base).getApp();
    }

    static long firstLong(JSONObject json, String... keys) {
        return firstLong(json, null, keys);
    }

    private static long firstLong(JSONObject primary, JSONObject secondary, String... keys) {
        for (String key : keys) {
            long value = primary == null ? 0 : primary.optLong(key, 0);
            if (value > 0) {
                return value;
            }
            value = secondary == null ? 0 : secondary.optLong(key, 0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    static List<String> parseScreenshotUrls(JSONObject json) {
        List<String> screenshots = new ArrayList<>();
        appendScreenshotText(json.optString("screenshot", ""), screenshots);
        JSONArray values = json.optJSONArray("screenshots");
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                appendScreenshotText(values.optString(index, ""), screenshots);
            }
        }
        return screenshots;
    }

    private static List<com.hyper.market.model.DetailComment> parseComments(
            JSONObject response, JSONObject data, JSONObject briefData) {
        return XiaomiDetailExtrasParser.parseComments(response, data, briefData);
    }

    private static List<MarketAppInfo> parseSameDeveloperApps(JSONObject response, JSONObject data) {
        return XiaomiDetailExtrasParser.parseSameDeveloperApps(response, data);
    }

    private static List<com.hyper.market.model.DetailPromotion> parsePromotions(
            JSONObject response, JSONObject data) {
        return XiaomiDetailExtrasParser.parsePromotions(response, data);
    }

    private static MarketAppInfo.Builder copyApp(MarketAppInfo app) {
        return new MarketAppInfo.Builder()
                .appId(app.getAppId())
                .packageName(app.getPackageName())
                .displayName(app.getDisplayName())
                .publisherName(app.getPublisherName())
                .versionName(app.getVersionName())
                .versionCode(app.getVersionCode())
                .iconUrl(app.getIconUrl())
                .apkSize(app.getApkSize())
                .ratingScore(app.getRatingScore())
                .changeLog(app.getChangeLog())
                .ad(app.isAd())
                .quickApp(app.isQuickApp())
                .reservationApp(app.isReservationApp())
                .introduction(app.getIntroduction())
                .downloadCount(app.getDownloadCount())
                .commentCount(app.getCommentCount())
                .ageClassification(app.getAgeClassification())
                .updateTime(app.getUpdateTime())
                .registrationNumber(app.getRegistrationNumber());
    }

    private static String privacyUrl(JSONObject response, JSONObject data, JSONObject briefData) {
        JSONObject[] sources = {response, data, briefData};
        for (JSONObject source : sources) {
            if (source == null) {
                continue;
            }
            JSONObject extra = source.optJSONObject("extraData");
            String value = firstText(source, "privacyUrl");
            if (value.isEmpty() && extra != null) {
                value = extra.optString("privacyUrl", "");
            }
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static JSONObject firstObject(JSONObject primary, JSONObject secondary, String key) {
        JSONObject value = primary == null ? null : primary.optJSONObject(key);
        return value == null && secondary != null ? secondary.optJSONObject(key) : value;
    }

    private static String firstText(JSONObject json, String... keys) {
        if (json == null) {
            return "";
        }
        for (String key : keys) {
            String value = json.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static void appendScreenshots(JSONArray list, List<String> screenshots) {
        if (list == null) {
            return;
        }
        for (int index = 0; index < list.length(); index++) {
            JSONObject item = list.optJSONObject(index);
            if (item != null) {
                appendScreenshotText(item.optString("screenshot", ""), screenshots);
            }
        }
    }

    private static void appendScreenshotText(String value, List<String> screenshots) {
        if (value.isEmpty()) {
            return;
        }
        for (String item : value.split(",")) {
            String normalized = normalizeImage(item.trim(), "l720q80");
            if (!normalized.isEmpty() && !screenshots.contains(normalized)) {
                screenshots.add(normalized);
            }
        }
    }

    private static String normalizeImage(String value, String size) {
        value = value.trim().replace("\\/", "/");
        if (value.startsWith("http://")) {
            return upgradeMarketImage(value);
        }
        if (value.isEmpty() || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("www.")) {
            return "https://" + value;
        }
        if (value.startsWith("webp/") || value.startsWith("png/")) {
            return "https://sf0.market.xiaomi.com/thumbnail/" + value;
        }
        return "https://sf0.market.xiaomi.com/thumbnail/webp/" + size + "/" + value;
    }

    private static String upgradeMarketImage(String value) {
        String host = value.substring("http://".length()).split("/", 2)[0].toLowerCase();
        if (host.equals("market.xiaomi.com") || host.endsWith(".market.xiaomi.com")) {
            return "https://" + value.substring("http://".length());
        }
        return value;
    }
}
