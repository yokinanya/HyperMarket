package com.hyper.market.api;

import com.hyper.market.model.DetailComment;
import com.hyper.market.model.DetailPromotion;
import com.hyper.market.model.DetailVideo;
import com.hyper.market.model.MarketAppInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class XiaomiDetailExtrasParser {
    private XiaomiDetailExtrasParser() { }

    static List<DetailComment> parseComments(JSONObject response, JSONObject data, JSONObject briefData) {
        List<DetailComment> comments = new ArrayList<>();
        appendComments(response, comments);
        appendComments(data, comments);
        appendComments(briefData, comments);
        return comments;
    }

    static List<MarketAppInfo> parseSameDeveloperApps(JSONObject response, JSONObject data) {
        List<MarketAppInfo> apps = new ArrayList<>();
        appendRelatedApps(response, apps);
        appendRelatedApps(data, apps);
        return apps;
    }

    static List<DetailPromotion> parsePromotions(JSONObject response, JSONObject data) {
        List<DetailPromotion> promotions = new ArrayList<>();
        appendBlockPromotions(response.optJSONArray("detailVideoAndScreenshotList"), promotions);
        if (data != null) {
            appendBlockPromotions(data.optJSONArray("detailVideoAndScreenshotList"), promotions);
        }
        appendPromotionArray(response, promotions);
        appendPromotionArray(data, promotions);
        return promotions;
    }

    static List<DetailVideo> parseVideos(JSONObject response, JSONObject data) {
        List<DetailVideo> videos = new ArrayList<>();
        appendVideos(response.optJSONArray("detailVideoAndScreenshotList"), videos);
        if (data != null) {
            appendVideos(data.optJSONArray("detailVideoAndScreenshotList"), videos);
        }
        return videos;
    }

    private static void appendVideos(JSONArray blocks, List<DetailVideo> videos) {
        if (blocks == null) return;
        for (int index = 0; index < blocks.length(); index++) {
            JSONObject block = blocks.optJSONObject(index);
            JSONObject video = block == null ? null : block.optJSONObject("appVideoInfoWithCover");
            if (video == null || !video.optBoolean("showVideo", true)) continue;
            String url = video.optString("videoUrl", "");
            if (url.isEmpty()) continue;
            String cover = normalizeImage(video.optString("coverUrl", ""), "l720q80");
            String title = block.optString("displayName", "");
            boolean exists = videos.stream().anyMatch(item -> item.getVideoUrl().equals(url));
            if (!exists) videos.add(new DetailVideo(url, cover, title));
        }
    }

    private static void appendComments(JSONObject source, List<DetailComment> comments) {
        if (source == null) return;
        String[] keys = {"comments", "commentList", "appCommentList", "userComments"};
        for (String key : keys) {
            JSONArray values = source.optJSONArray(key);
            if (values == null) continue;
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                String user = firstText(value, "userName", "userNickname", "nickname", "name");
                String content = firstText(value, "content", "comment", "text");
                if (content.isEmpty()) continue;
                double score = value.optDouble("score", value.optDouble("rating", 0));
                boolean exists = comments.stream().anyMatch(item ->
                        item.getUserName().equals(user) && item.getContent().equals(content));
                if (!exists) comments.add(new DetailComment(user, content, score));
            }
        }
    }

    private static void appendRelatedApps(JSONObject source, List<MarketAppInfo> apps) {
        if (source == null) return;
        String[] keys = {"sameDeveloperApps", "sameDeveloperAppList", "sameDeveloper"};
        for (String key : keys) {
            JSONArray values = source.optJSONArray(key);
            if (values == null) continue;
            for (int index = 0; index < values.length(); index++) {
                MarketAppInfo app = parseRelatedApp(values.optJSONObject(index));
                if (app == null) continue;
                boolean exists = apps.stream().anyMatch(item ->
                        item.getPackageName().equals(app.getPackageName()));
                if (!exists) apps.add(app);
            }
        }
    }

    private static void appendBlockPromotions(JSONArray blocks, List<DetailPromotion> promotions) {
        if (blocks == null) return;
        for (int index = 0; index < blocks.length(); index++) {
            JSONObject block = blocks.optJSONObject(index);
            if (block != null && block.optInt("type", 0) == 8) {
                addPromotion(block.optJSONObject("appActivityConfig"), promotions);
            }
        }
    }

    private static void appendPromotionArray(JSONObject source, List<DetailPromotion> promotions) {
        if (source == null) return;
        String[] keys = {"promotions", "promotionList", "activityList", "appActivityConfig"};
        for (String key : keys) {
            JSONArray values = source.optJSONArray(key);
            if (values == null) continue;
            for (int index = 0; index < values.length(); index++) {
                addPromotion(values.optJSONObject(index), promotions);
            }
        }
    }

    private static void addPromotion(JSONObject source, List<DetailPromotion> promotions) {
        if (source == null) return;
        String preview = normalizeImage(
                firstText(source, "previewImageUrl", "previewImage", "screenshot"), "l720q80");
        String expanded = normalizeImage(
                firstText(source, "expandedImageUrl", "expandScreenshot"), "q90");
        String title = firstText(source, "title", "mainTitle");
        String description = firstText(source, "description", "mainText", "subTitle");
        String jumpUrl = firstText(source, "jumpUrl", "linkUrl", "url");
        if (title.isEmpty() && description.isEmpty() && preview.isEmpty() && jumpUrl.isEmpty()) return;
        boolean exists = promotions.stream().anyMatch(item -> item.getTitle().equals(title)
                && item.getJumpUrl().equals(jumpUrl));
        if (!exists) {
            promotions.add(new DetailPromotion(
                    preview, expanded, title, description,
                    firstText(source, "category", "activityShowType"),
                    firstText(source, "activityTag", "tag"), jumpUrl));
        }
    }

    private static MarketAppInfo parseRelatedApp(JSONObject json) {
        if (json == null) return null;
        String packageName = json.optString("packageName", "");
        if (packageName.isEmpty()) return null;
        return new MarketAppInfo.Builder()
                .appId(json.optLong("appId", json.optLong("id", 0)))
                .packageName(packageName)
                .displayName(firstText(json, "appName", "displayName", "name"))
                .publisherName(firstText(json, "publisherName", "developerName"))
                .versionName(json.optString("versionName", ""))
                .versionCode(json.optLong("versionCode", 0))
                .iconUrl(normalizeImage(json.optString("icon", ""), "l144q80"))
                .apkSize(json.optLong("apkSize", json.optLong("apkSizeV2", 0)))
                .ratingScore(json.optDouble("ratingScore", json.optDouble("commentScore", 0)))
                .introduction(firstText(json, "briefShow", "introduction"))
                .build();
    }

    private static String firstText(JSONObject json, String... keys) {
        if (json == null) return "";
        for (String key : keys) {
            String value = json.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String normalizeImage(String value, String size) {
        value = value.trim().replace("\\/", "/");
        if (value.startsWith("http://")) return upgradeMarketImage(value);
        if (value.isEmpty() || value.startsWith("https://")) return value;
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("www.")) return "https://" + value;
        if (value.startsWith("webp/") || value.startsWith("png/")) {
            return "https://sf0.market.xiaomi.com/thumbnail/" + value;
        }
        return "https://sf0.market.xiaomi.com/thumbnail/webp/" + size + "/" + value;
    }

    private static String upgradeMarketImage(String value) {
        String host = value.substring(7).split("/", 2)[0].toLowerCase();
        if (host.equals("market.xiaomi.com") || host.endsWith(".market.xiaomi.com")) {
            return "https://" + value.substring(7);
        }
        return value;
    }
}
