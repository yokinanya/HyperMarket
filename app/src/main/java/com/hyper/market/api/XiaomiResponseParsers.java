package com.hyper.market.api;

import com.hyper.market.model.ApkArtifact;
import com.hyper.market.model.DownloadMetadata;
import com.hyper.market.model.InstalledPackageInfo;
import com.hyper.market.model.MarketAppInfo;
import com.hyper.market.model.TodayArticle;
import com.hyper.market.model.TodayFeedPage;
import com.hyper.market.model.TodayFeaturedItem;
import com.hyper.market.model.UpdateInfo;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应解析层（从 XiaomiApiClient 拆分）。
 * 今日/金米奖/文章/更新/搜索/应用/下载响应解析，全部为无状态静态方法。
 * 网络/参数/设备工具见 XiaomiApiSupport，客户端状态见 XiaomiApiClient。
 */
final class XiaomiResponseParsers {
    private static final String TAG = "XiaomiApi";
    private static final long OPEN_ENDED_APP_VERSION = 2_147_483_647L;
    private static final String QUICK_GAME_TYPE = "quickGame";
    private static final Pattern IMAGE_SOURCE_PATTERN = Pattern.compile(
            "(?i)<img\\b[^>]*(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"'][^>]*>");

    private XiaomiResponseParsers() {
    }

    static Map<String, Long> parseDiffSizes(JSONObject response) {
        JSONArray values = response.optJSONArray("apkDiffInfoList");
        JSONObject data = response.optJSONObject("data");
        if (values == null && data != null) values = data.optJSONArray("apkDiffInfoList");
        Map<String, Long> result = new LinkedHashMap<>();
        if (values == null) return result;
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            String packageName = item.optString("packageName", "");
            long size = item.optLong("diffFileSize", 0L);
            if (!XiaomiApiSupport.isBlank(packageName) && size > 0) result.put(packageName, size);
        }
        return result;
    }

    static TodayFeedPage parseTodayFeed(JSONObject response) throws java.io.IOException {
        JSONArray groups = response.optJSONArray("list");
        if (groups == null) {
            throw new java.io.IOException("Xiaomi API today response has no list");
        }
        List<TodayFeaturedItem> items = new ArrayList<>();
        boolean hasMore = response.optBoolean("hasMore", false);
        for (int groupIndex = 0; groupIndex < groups.length(); groupIndex++) {
            JSONObject group = groups.optJSONObject(groupIndex);
            JSONObject data = group == null ? null : group.optJSONObject("data");
            JSONArray featured = data == null ? null : data.optJSONArray("topfeaturedList");
            if (featured == null || featured.length() == 0) {
                if (data != null) {
                    hasMore = hasMore || data.optBoolean("hasMore", false);
                }
                appendPrimaryItem(data, items);
                continue;
            }
            hasMore = hasMore || data.optBoolean("hasMore", false);
            for (int itemIndex = 0; itemIndex < featured.length(); itemIndex++) {
                JSONObject item = featured.optJSONObject(itemIndex);
                if (item != null) {
                    TodayFeaturedItem parsed = parseTodayFeaturedItem(item);
                    if (parsed != null) {
                        items.add(parsed);
                    }
                }
            }
        }
        return new TodayFeedPage(uniqueTodayItems(items), hasMore);
    }

    private static void appendPrimaryItem(JSONObject data, List<TodayFeaturedItem> items)
            throws java.io.IOException {
        if (data == null) return;
        TodayFeaturedItem parsed = parseTodayFeaturedItem(data);
        if (parsed != null) items.add(parsed);
    }

    static TodayFeedPage parseGoldMiFeed(JSONObject response) throws java.io.IOException {
        JSONArray groups = response.optJSONArray("list");
        if (groups == null) {
            throw new java.io.IOException("Xiaomi API goldMi response has no list");
        }
        List<TodayFeaturedItem> items = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groups.length(); groupIndex++) {
            JSONObject group = groups.optJSONObject(groupIndex);
            JSONObject data = group == null ? null : group.optJSONObject("data");
            JSONArray apps = data == null ? null : data.optJSONArray("listApp");
            if (apps == null) {
                continue;
            }
            for (int itemIndex = 0; itemIndex < apps.length(); itemIndex++) {
                JSONObject item = apps.optJSONObject(itemIndex);
                TodayFeaturedItem parsed = item == null ? null : parseGoldMiItem(data, item);
                if (parsed != null) {
                    items.add(parsed);
                }
            }
        }
        return new TodayFeedPage(
                uniqueTodayItems(items),
                response.optBoolean("hasMore", !items.isEmpty()));
    }

    private static TodayFeaturedItem parseTodayFeaturedItem(JSONObject item) throws java.io.IOException {
        JSONObject bannerInfo = item.optJSONObject("bannerInfo");
        String image = firstUsableImage(bannerInfo, "banner", "image", "url");
        if (image.isEmpty()) {
            image = firstUsableImage(
                    item, "banner", "thumbnail", "mticon", "webViewPic", "imgUrl", "headImg");
        }
        String clickUrl = todayClickUrl(item);
        String resourceId = resourceIdFromUrl(clickUrl);
        if (resourceId.isEmpty()) {
            resourceId = decodeResourceId(firstText(item, "rId", "resourceId"));
        }
        List<MarketAppInfo> apps = parseTodayApps(item);
        MarketAppInfo app = apps.isEmpty() ? null : apps.get(0);
        String title = firstText(item, "title", "detailTitle", "outerTitle", "webViewTitle");
        String summary = firstText(item, "summary", "subTitle");
        String normalizedImage = normalizeImage(image, "l720q90");
        if (clickUrl.isEmpty() && app == null && title.isEmpty()
                && summary.isEmpty() && normalizedImage.isEmpty()) {
            return null;
        }
        return new TodayFeaturedItem(
                resourceId,
                title,
                summary,
                normalizedImage,
                clickUrl,
                app,
                apps,
                false);
    }

    private static TodayFeaturedItem parseGoldMiItem(JSONObject data, JSONObject item)
            throws java.io.IOException {
        String clickUrl = todayClickUrl(item);
        String resourceId = resourceIdFromUrl(clickUrl);
        if (resourceId.isEmpty()) {
            resourceId = decodeResourceId(firstText(item, "rId", "resourceId"));
        }
        MarketAppInfo app = parseOptionalApp(item);
        String title = firstText(data, "title", "linkTitle");
        if (title.isEmpty()) {
            title = firstText(item, "card_title", "title", "linkTitle");
        }
        String summary = firstText(item, "description", "summary", "subTitle");
        String image = firstUsableImage(
                item, "imgUrl", "banner", "thumbnail", "mticon", "webViewPic");
        if (resourceId.isEmpty() && clickUrl.isEmpty() && app == null) {
            return null;
        }
        List<MarketAppInfo> apps = app == null
                ? Collections.emptyList() : Collections.singletonList(app);
        return new TodayFeaturedItem(
                resourceId,
                title,
                summary,
                normalizeImage(image, "l720q90"),
                clickUrl,
                app,
                apps,
                true);
    }

    static TodayFeedPage interleaveTodayFeeds(TodayFeedPage primary, TodayFeedPage goldMi) {
        List<TodayFeaturedItem> merged = new ArrayList<>();
        int count = Math.max(primary.getItems().size(), goldMi.getItems().size());
        for (int index = 0; index < count; index++) {
            if (index < primary.getItems().size()) {
                merged.add(primary.getItems().get(index));
            }
            if (index < goldMi.getItems().size()) {
                merged.add(goldMi.getItems().get(index));
            }
        }
        return new TodayFeedPage(
                uniqueTodayItems(merged),
                primary.hasMore() || goldMi.hasMore());
    }

    static java.io.IOException combinedTodayFailure(
            java.io.IOException feedFailure, java.io.IOException goldMiFailure) {
        String primaryMessage = failureMessage(feedFailure, "主今日接口失败");
        String goldMiMessage = failureMessage(goldMiFailure, "金米奖接口失败");
        return new java.io.IOException(
                "今日内容加载失败：" + primaryMessage + "；" + goldMiMessage, feedFailure);
    }

    private static String failureMessage(java.io.IOException failure, String fallback) {
        return failure == null || failure.getMessage() == null
                ? fallback : failure.getMessage();
    }

    static TodayFeedPage emptyTodayPage() {
        return new TodayFeedPage(Collections.emptyList(), false);
    }

    private static List<MarketAppInfo> parseTodayApps(JSONObject item) throws java.io.IOException {
        List<MarketAppInfo> apps = new ArrayList<>();
        MarketAppInfo directApp = parseOptionalApp(item);
        if (directApp != null) {
            apps.add(directApp);
        }
        appendAppList(item.optJSONArray("listApp"), apps);
        appendAppList(item.optJSONArray("appList"), apps);
        return uniqueApps(apps);
    }

    private static List<TodayFeaturedItem> uniqueTodayItems(List<TodayFeaturedItem> items) {
        Set<String> keys = new LinkedHashSet<>();
        List<TodayFeaturedItem> unique = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            TodayFeaturedItem item = items.get(index);
            String key = todayItemKey(item, index);
            if (keys.add(key)) {
                unique.add(item);
            }
        }
        return unique;
    }

    private static String todayItemKey(TodayFeaturedItem item, int index) {
        if (!item.getResourceId().isEmpty()) return "resource:" + item.getResourceId();
        if (!item.getClickUrl().isEmpty()) return "url:" + item.getClickUrl();
        if (item.getApp() != null) return "app:" + item.getApp().getPackageName();
        if (!item.getTitle().isEmpty()) return "title:" + item.getTitle();
        return "position:" + index;
    }

    static TodayArticle parseTodayArticle(String resourceId, JSONObject response)
            throws java.io.IOException {
        JSONObject data = firstArticleData(response);
        JSONArray blocks = data.optJSONArray("topicItemList");
        if (blocks == null) {
            throw new java.io.IOException("Xiaomi API article response has no topicItemList");
        }
        JSONObject bannerInfo = data.optJSONObject("bannerInfo");
        String headerImage = firstUsableImage(
                bannerInfo, "banner", "image", "url");
        if (headerImage.isEmpty()) {
            headerImage = firstUsableImage(
                    data, "headerImage", "headerImagePreview", "banner", "thumbnail", "mticon", "webViewPic");
        }
        StringBuilder body = new StringBuilder();
        List<String> imageUrls = new ArrayList<>();
        List<MarketAppInfo> apps = new ArrayList<>();
        for (int index = 0; index < blocks.length(); index++) {
            JSONObject block = blocks.optJSONObject(index);
            if (block == null) {
                continue;
            }
            if (headerImage.isEmpty()) {
                headerImage = articleBannerImage(block);
            }
            parseArticleBlock(block, body, imageUrls, apps);
        }
        return new TodayArticle(
                resourceId,
                firstText(data, "title", "detailTitle", "outerTitle", "webViewTitle"),
                normalizeImage(headerImage, "q90"),
                body.toString(),
                imageUrls,
                uniqueApps(apps));
    }

    private static JSONObject firstArticleData(JSONObject response) throws java.io.IOException {
        JSONArray list = response.optJSONArray("list");
        JSONObject first = list == null ? null : list.optJSONObject(0);
        JSONObject data = first == null ? null : first.optJSONObject("data");
        if (data == null) {
            throw new java.io.IOException("Xiaomi API article response has no data");
        }
        return data;
    }

    private static void parseArticleBlock(
            JSONObject block,
            StringBuilder body,
            List<String> imageUrls,
            List<MarketAppInfo> apps) throws java.io.IOException {
        String type = block.optString("topicItemType", "");
        if (type.equals("topicBanner")) {
            return;
        }
        if (type.equals("topicImage")) {
            String image = firstUsableImage(block, "imgUrl", "thumbnail", "mticon", "webViewPic");
            JSONObject imageInfo = block.optJSONObject("imageInfo");
            if (image.isEmpty() && imageInfo != null) {
                image = firstUsableImage(imageInfo, "image", "url", "imgUrl", "mticon", "webViewPic");
            }
            if (!image.isEmpty()) {
                addImageUrl(imageUrls, normalizeImage(image, "q90"));
            }
            return;
        }
        if (type.equals("topicRichText")) {
            JSONObject richText = block.optJSONObject("richTextInfo");
            String text = richText == null ? "" : firstText(richText, "desc", "content");
            if (text.isEmpty()) {
                text = firstText(block, "desc", "content");
            }
            appendBody(body, text);
            appendRichTextImages(text, imageUrls);
            return;
        }
        MarketAppInfo app = parseOptionalApp(block);
        if (app != null) {
            apps.add(app);
        }
        JSONObject appInfo = block.optJSONObject("appInfo");
        if (appInfo != null) {
            app = parseOptionalApp(appInfo);
            if (app != null) {
                apps.add(app);
            }
        }
        appendAppList(block.optJSONArray("listApp"), apps);
        appendAppList(block.optJSONArray("appList"), apps);
    }

    private static String articleBannerImage(JSONObject block) {
        JSONObject bannerInfo = block.optJSONObject("bannerInfo");
        String image = firstUsableImage(
                bannerInfo, "banner", "mticon", "webViewPic", "imgUrl");
        if (image.isEmpty()) {
            image = firstUsableImage(
                    block, "banner", "mticon", "webViewPic", "imgUrl");
        }
        return image;
    }

    private static void appendAppList(JSONArray list, List<MarketAppInfo> apps)
            throws java.io.IOException {
        if (list == null) {
            return;
        }
        for (int index = 0; index < list.length(); index++) {
            JSONObject appJson = list.optJSONObject(index);
            MarketAppInfo app = appJson == null ? null : parseOptionalApp(appJson);
            if (app != null) {
                apps.add(app);
            }
        }
    }

    private static void appendBody(StringBuilder body, String text) {
        if (text.isEmpty()) {
            return;
        }
        if (body.length() > 0) {
            body.append("<br><br>");
        }
        body.append(text);
    }

    private static void appendRichTextImages(String html, List<String> imageUrls) {
        Matcher matcher = IMAGE_SOURCE_PATTERN.matcher(html);
        while (matcher.find()) {
            addImageUrl(imageUrls, normalizeImage(matcher.group(1), "q90"));
        }
    }

    private static void addImageUrl(List<String> imageUrls, String imageUrl) {
        if (!imageUrl.isEmpty() && !imageUrls.contains(imageUrl)) {
            imageUrls.add(imageUrl);
        }
    }

    private static List<MarketAppInfo> uniqueApps(List<MarketAppInfo> apps) {
        Set<String> packages = new LinkedHashSet<>();
        List<MarketAppInfo> unique = new ArrayList<>();
        for (MarketAppInfo app : apps) {
            if (packages.add(app.getPackageName())) {
                unique.add(app);
            }
        }
        return unique;
    }

    private static MarketAppInfo parseOptionalApp(JSONObject source) throws java.io.IOException {
        JSONObject app = source.optJSONObject("appInfo");
        JSONObject candidate = app == null ? source : app;
        if (candidate.optString("packageName", "").isEmpty()) {
            return null;
        }
        return parseApp(candidate);
    }

    private static String resourceIdFromUrl(String url) {
        int marker = url.indexOf("rId=");
        if (marker < 0) {
            return "";
        }
        String value = url.substring(marker + 4);
        int end = value.indexOf('&');
        return decodeResourceId(end < 0 ? value : value.substring(0, end));
    }

    private static String decodeResourceId(String value) {
        if (value.isEmpty()) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is unavailable", exception);
        }
    }

    static JSONArray findSearchList(JSONObject response) {
        JSONArray list = response.optJSONArray("list");
        JSONObject data = response.optJSONObject("data");
        if (list == null && data != null) {
            list = data.optJSONArray("listApp");
        }
        if (list == null && data != null) {
            list = data.optJSONArray("appList");
        }
        return list;
    }

    static JSONObject findObject(JSONObject response) {
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            JSONObject app = data.optJSONObject("appInfo");
            if (app != null) {
                return app;
            }
        }
        JSONObject app = response.optJSONObject("appInfo");
        if (app != null) {
            return app;
        }
        return response;
    }

    static List<MarketAppInfo> parseSearchApps(JSONArray list) {
        List<MarketAppInfo> apps = new ArrayList<>();
        Set<String> packages = new LinkedHashSet<>();
        for (int index = 0; index < list.length(); index++) {
            JSONObject item = list.optJSONObject(index);
            if (item == null) {
                continue;
            }
            for (MarketAppInfo app : extractSearchApps(item)) {
                if (packages.add(app.getPackageName())) {
                    apps.add(app);
                }
            }
        }
        return apps;
    }

    static List<UpdateInfo> parseUpdates(
            JSONObject response,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage)
            throws java.io.IOException {
        List<UpdateInfo> updates = new ArrayList<>();
        appendUpdates(response.optJSONArray("listApp"), installedByPackage, updates);
        appendUpdates(response.optJSONArray("miuiApp"), installedByPackage, updates);
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            appendUpdates(data.optJSONArray("listApp"), installedByPackage, updates);
            appendUpdates(data.optJSONArray("miuiApp"), installedByPackage, updates);
        }
        return uniqueUpdates(updates);
    }

    private static void appendUpdates(
            JSONArray items,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage,
            List<UpdateInfo> updates) throws java.io.IOException {
        if (items == null) {
            return;
        }
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                continue;
            }
            UpdateInfo update = parseUpdate(item, installedByPackage);
            if (update != null) {
                updates.add(update);
            }
        }
    }

    private static UpdateInfo parseUpdate(
            JSONObject item,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage)
            throws java.io.IOException {
        JSONObject candidate = unwrapUpdateItem(item);
        String packageName = candidate.optString("packageName", "");
        InstalledPackageInfo installed = installedByPackage.get(packageName);
        if (installed == null) {
            return null;
        }
        MarketAppInfo app = parseUpdateApp(candidate, installed.getVersionCode());
        if (app == null || app.getVersionCode() <= installed.getVersionCode()) {
            return null;
        }
        JSONObject fitnessApk = selectFitnessApk(candidate, installed.getVersionCode());
        long diffSize = fitnessApk == null
                ? candidate.optLong("diffSize", candidate.optLong("patchSize", 0))
                : fitnessApk.optLong("diffSize", fitnessApk.optLong("patchSize", 0));
        return new UpdateInfo(app, installed, diffSize);
    }

    private static MarketAppInfo parseUpdateApp(JSONObject candidate, long installedVersionCode)
            throws java.io.IOException {
        if (candidate.optString("packageName", "").isEmpty()) {
            return null;
        }
        MarketAppInfo base = parseApp(candidate);
        JSONObject fitnessApk = selectFitnessApk(candidate, installedVersionCode);
        if (fitnessApk == null) {
            return base;
        }
        long versionCode = fitnessApk.optLong("versionCode", base.getVersionCode());
        String versionName = firstText(fitnessApk, "versionName", "displayVersion");
        long apkSize = fitnessApk.optLong(
                "apkSize", fitnessApk.optLong("apkSizeV2", base.getApkSize()));
        String icon = firstText(fitnessApk, "icon");
        return copyApp(base)
                .versionName(versionName.isEmpty() ? base.getVersionName() : versionName)
                .versionCode(versionCode)
                .apkSize(apkSize)
                .iconUrl(icon.isEmpty() ? base.getIconUrl() : normalizeImage(icon))
                .build();
    }

    private static JSONObject selectFitnessApk(JSONObject candidate, long installedVersionCode) {
        JSONObject fitnessApks = candidate.optJSONObject("fitnessApks");
        JSONArray apks = fitnessApks == null ? null : fitnessApks.optJSONArray("apks");
        if (apks == null) {
            return null;
        }
        JSONObject matching = highestApk(apks, installedVersionCode, true);
        if (matching != null) return matching;
        JSONObject openEnded = highestOpenEndedApk(apks);
        return openEnded == null ? highestApk(apks, installedVersionCode, false) : openEnded;
    }

    private static JSONObject highestOpenEndedApk(JSONArray apks) {
        JSONObject highest = null;
        long highestVersionCode = 0;
        for (int index = 0; index < apks.length(); index++) {
            JSONObject apk = apks.optJSONObject(index);
            if (apk == null || apk.optBoolean("downloadDisable", false)) continue;
            long minimum = apk.optLong("minAppVersion", Long.MIN_VALUE);
            long maximum = apk.optLong("maxAppVersion", OPEN_ENDED_APP_VERSION);
            long versionCode = apk.optLong("versionCode", 0);
            if (minimum <= 0 || maximum < OPEN_ENDED_APP_VERSION || versionCode <= 0) continue;
            if (highest == null || versionCode > highestVersionCode) {
                highest = apk;
                highestVersionCode = versionCode;
            }
        }
        return highest;
    }

    private static JSONObject highestApk(JSONArray apks, long installedVersionCode, boolean enforceRange) {
        JSONObject highest = null;
        long highestVersionCode = 0;
        for (int index = 0; index < apks.length(); index++) {
            JSONObject apk = apks.optJSONObject(index);
            if (apk == null || apk.optBoolean("downloadDisable", false)) {
                continue;
            }
            long versionCode = apk.optLong("versionCode", 0);
            if (versionCode <= 0 || (enforceRange && !matchesVersionRange(apk, installedVersionCode))) {
                continue;
            }
            if (highest == null || versionCode > highestVersionCode) {
                highest = apk;
                highestVersionCode = versionCode;
            }
        }
        return highest;
    }

    private static boolean matchesVersionRange(JSONObject apk, long installedVersionCode) {
        long minimum = apk.optLong("minAppVersion", Long.MIN_VALUE);
        long maximum = apk.optLong("maxAppVersion", Long.MAX_VALUE);
        if (minimum <= 0) {
            minimum = Long.MIN_VALUE;
        }
        if (maximum <= 0) {
            maximum = Long.MAX_VALUE;
        }
        return minimum <= installedVersionCode && installedVersionCode <= maximum;
    }

    private static List<UpdateInfo> uniqueUpdates(List<UpdateInfo> updates) {
        Set<String> packages = new LinkedHashSet<>();
        List<UpdateInfo> unique = new ArrayList<>();
        for (UpdateInfo update : updates) {
            if (packages.add(update.getApp().getPackageName())) {
                unique.add(update);
            }
        }
        return unique;
    }

    private static List<MarketAppInfo> extractSearchApps(JSONObject item) {
        JSONObject data = item.optJSONObject("data");
        JSONArray nested = data == null ? null : data.optJSONArray("listApp");
        if (nested == null && data != null) {
            nested = data.optJSONArray("appList");
        }
        List<MarketAppInfo> apps = new ArrayList<>();
        if (nested == null) {
            MarketAppInfo app = tryParseApp(unwrapSearchItem(item));
            if (app != null) {
                apps.add(app);
            }
            return apps;
        }
        for (int index = 0; index < nested.length(); index++) {
            MarketAppInfo app = tryParseApp(unwrapSearchItem(nested.optJSONObject(index)));
            if (app != null) {
                apps.add(app);
            }
        }
        return apps;
    }

    private static MarketAppInfo tryParseApp(JSONObject json) {
        if (json == null || json.optString("packageName", "").isEmpty()) {
            return null;
        }
        try {
            return parseApp(json);
        } catch (java.io.IOException exception) {
            Log.w(TAG, "skip invalid search app", exception);
            return null;
        }
    }

    private static JSONObject unwrapSearchItem(JSONObject item) {
        JSONObject data = item.optJSONObject("data");
        if (data == null) {
            return item;
        }
        JSONObject appInfo = data.optJSONObject("appInfo");
        return appInfo == null ? data : appInfo;
    }

    private static JSONObject unwrapUpdateItem(JSONObject item) {
        JSONObject data = item.optJSONObject("data");
        if (data == null) {
            return item;
        }
        JSONObject appInfo = data.optJSONObject("appInfo");
        return appInfo == null ? data : appInfo;
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
                .registrationNumber(app.getRegistrationNumber())
                .screenshotUrls(app.getScreenshotUrls());
    }

    static MarketAppInfo parseApp(JSONObject json) throws java.io.IOException {
        String packageName = json.optString("packageName", "");
        if (packageName.isEmpty()) {
            throw new java.io.IOException("Xiaomi API app has no packageName");
        }
        return new MarketAppInfo.Builder()
                .appId(json.optLong("appId", json.optLong("id", 0)))
                .packageName(packageName)
                .displayName(firstText(json, "appName", "displayName", "name"))
                .publisherName(firstText(json, "publisherName", "developerName"))
                .versionName(json.optString("versionName", ""))
                .versionCode(json.optLong("versionCode", 0))
                .iconUrl(normalizeImage(firstText(
                        json, "icon", "iconUrl", "mticon", "webViewPic", "imgUrl")))
                .apkSize(json.optLong("apkSize", json.optLong("apkSizeV2", json.optLong("size", 0))))
                .ratingScore(json.optDouble("commentScore", json.optDouble("ratingScore", 0)))
                .changeLog(json.optString("changeLog", ""))
                .ad(json.optBoolean("isSearchTopAd", false))
                .quickApp(isQuickApp(json))
                .reservationApp(isReservationApp(json))
                .introduction(firstText(json, "briefShow", "introduction", "description"))
                .downloadCount(json.optLong("downloadCount", 0))
                .commentCount(XiaomiDetailParser.firstLong(
                        json, "ratingTotalCount", "commentCount", "totalCommentCount", "commentNum"))
                .ageClassification(json.optString("ageClassification", ""))
                .updateTime(json.optLong("updateTime", 0))
                .registrationNumber(json.optString("registrationNum", ""))
                .screenshotUrls(XiaomiDetailParser.parseScreenshotUrls(json))
                .build();
    }

    private static boolean isQuickApp(JSONObject json) {
        return json.optBoolean("isQuickApp", json.optBoolean("quickApp", false))
                || QUICK_GAME_TYPE.equals(json.optString("type", ""));
    }

    private static boolean isReservationApp(JSONObject json) {
        boolean explicit = json.optBoolean(
                "isReservationApp", json.optBoolean("reservationApp", false));
        long versionCode = json.optLong("versionCode", 0);
        int subscribeState = json.optInt("subscribeState", 0);
        return explicit || (subscribeState > 0 && versionCode <= 0);
    }

    static DownloadMetadata parseDownload(MarketAppInfo app, JSONObject response)
            throws java.io.IOException {
        JSONObject data = response.optJSONObject("data");
        JSONObject source = response;
        JSONArray apks = response.optJSONArray("apks");
        if (apks == null && data != null) {
            source = data;
            apks = data.optJSONArray("apks");
        }
        if (apks == null) {
            String reason = firstText(source, "unfitnessDesc", "errorDesc", "message", "msg");
            throw new java.io.IOException(
                    reason.isEmpty() ? "下载失败：无 APK 包体" : "下载失败：" + reason);
        }
        String host = downloadHost(response, source);
        int diffVersion = response.optInt("bspatchVersion", source.optInt("bspatchVersion", 0));
        List<ApkArtifact> artifacts = parseArtifacts(apks, host, diffVersion);
        long totalSize = artifacts.stream().mapToLong(ApkArtifact::getSize).sum();
        return new DownloadMetadata(app, artifacts, totalSize,
                firstText(response, "versionName").isEmpty()
                        ? source.optString("versionName", app.getVersionName())
                        : response.optString("versionName"),
                response.optLong("versionCode", source.optLong("versionCode", app.getVersionCode())));
    }

    private static String downloadHost(JSONObject primary, JSONObject secondary) {
        JSONArray hosts = primary.optJSONArray("hosts");
        if (hosts == null) hosts = secondary.optJSONArray("hosts");
        String host = hosts == null ? "" : hosts.optString(0, "");
        return XiaomiApiSupport.isBlank(host) ? XiaomiApiSupport.DOWNLOAD_API : host;
    }

    private static List<ApkArtifact> parseArtifacts(JSONArray apks, String host, int diffVersion)
            throws java.io.IOException {
        List<ApkArtifact> artifacts = new ArrayList<>();
        for (int index = 0; index < apks.length(); index++) {
            try {
                JSONObject item = apks.getJSONObject(index);
                String url = firstText(item, "url", "fullUrl");
                if (url.isEmpty()) {
                    throw new java.io.IOException(
                            "Xiaomi API artifact has no url at index " + index);
                }
                String name = item.optString("name", "base");
                String type = item.optString("type", "base");
                String diffUrl = firstText(item, "diffUrl", "patchUrl");
                long diffSize = item.optLong("diffSize", item.optLong("patchSize", 0));
                String diffHash = firstText(item, "diffHash", "patchChecksum");
                int artifactDiffVersion = item.optInt("patchVersion", diffVersion);
                artifacts.add(new ApkArtifact(
                        name,
                        type,
                        normalizeDownload(url, host),
                        item.optLong("size", 0),
                        firstText(item, "hash", "checksum"),
                        normalizeDownload(diffUrl, host),
                        diffSize,
                        diffHash,
                        artifactDiffVersion,
                        item.optString("baseApkPath", "")));
            } catch (JSONException exception) {
                throw new java.io.IOException(
                        "Xiaomi API artifact is invalid at index " + index, exception);
            }
        }
        return artifacts;
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

    private static String firstUsableImage(JSONObject json, String... keys) {
        if (json == null) {
            return "";
        }
        for (String key : keys) {
            String value = json.optString(key, "").trim().replace("\\/", "/");
            if (!value.isEmpty() && !isThumbnailBaseUrl(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isThumbnailBaseUrl(String value) {
        String normalized = value.trim().replace("\\/", "/");
        String base = XiaomiApiSupport.THUMBNAIL_BASE_URL;
        return normalized.equalsIgnoreCase(base)
                || normalized.equalsIgnoreCase(base.substring(0, base.length() - 1));
    }

    private static String todayClickUrl(JSONObject item) {
        String clickUrl = firstText(item, "clientClickUrl", "webViewUrl", "clickUrl");
        if (clickUrl.isEmpty()) {
            clickUrl = firstText(item, "deeplink", "deepLink", "ext_deeplink", "inner_deeplink",
                    "deeplinkUrl", "deeplinkAfterInstall", "linkUrlWhenClick", "linkUrl", "link",
                    "actionUrl", "marketLink");
        }
        JSONObject extraData = item.optJSONObject("extraData");
        if (clickUrl.isEmpty()) {
            clickUrl = firstText(extraData, "deeplink", "deepLink", "ext_deeplink", "inner_deeplink",
                    "deeplinkUrl", "deeplinkAfterInstall", "linkUrlWhenClick", "linkUrl", "link");
        }
        return clickUrl;
    }

    private static String normalizeImage(String value) {
        return normalizeImage(value, "l144q80");
    }

    private static String normalizeImage(String value, String size) {
        value = value.trim().replace("\\/", "/");
        if (value.isEmpty() || isThumbnailBaseUrl(value)) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("www.")) {
            return "https://" + value;
        }
        if (value.startsWith("http://")) {
            return upgradeMarketImage(value);
        }
        if (value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("webp/") || value.startsWith("png/")) {
            return XiaomiApiSupport.THUMBNAIL_BASE_URL + value;
        }
        return XiaomiApiSupport.THUMBNAIL_BASE_URL + "webp/" + size + "/" + value;
    }

    private static String upgradeMarketImage(String value) {
        String host = value.substring("http://".length()).split("/", 2)[0].toLowerCase(Locale.ROOT);
        if (host.equals("market.xiaomi.com") || host.endsWith(".market.xiaomi.com")) {
            return "https://" + value.substring("http://".length());
        }
        return value;
    }

    private static String normalizeDownload(String value, String host) {
        if (value.isEmpty()) {
            return value;
        }
        if (value.startsWith("http")) {
            return value;
        }
        if (!host.endsWith("/")) {
            host += "/";
        }
        return host + value;
    }
}
