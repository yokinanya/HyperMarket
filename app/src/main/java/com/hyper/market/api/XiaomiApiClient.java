package com.hyper.market.api;

import com.hyper.market.model.ApkArtifact;
import com.hyper.market.model.DownloadMetadata;
import com.hyper.market.model.InstalledPackageInfo;
import com.hyper.market.model.MarketAppDetails;
import com.hyper.market.model.MarketAppInfo;
import com.hyper.market.model.TodayArticle;
import com.hyper.market.model.TodayFeedPage;
import com.hyper.market.model.TodayFeaturedItem;
import com.hyper.market.model.UpdateInfo;

import android.os.Build;
import android.content.Context;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;

public final class XiaomiApiClient {
    private static final String MARKET_API = "https://app.market.xiaomi.com/apm/";
    private static final String UPDATE_API =
            "https://updateinfo.market.xiaomi.com/apm/updateinfo/v2?lo=CN";
    private static final String DOWNLOAD_API = "https://fgb0.market.xiaomi.com/download/";
    private static final String THUMBNAIL_BASE_URL =
            "https://sf0.market.xiaomi.com/thumbnail/";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int GOLD_MI_PAGE_SIZE = 9;
    private static final String TAG = "XiaomiApi";
    private static final String USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 16; 25128PNA1C Build/BP2A.250605.031.A3)";
    private static final String INSTANCE_ID = UUID.randomUUID().toString();
    private static final Pattern IMAGE_SOURCE_PATTERN = Pattern.compile(
            "(?i)<img\\b[^>]*(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"'][^>]*>");
    private volatile java.util.Map<String, String> profileOverrides = Collections.emptyMap();
    private final Context context;

    public XiaomiApiClient() {
        this(null);
    }

    public XiaomiApiClient(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public void setProfileOverrides(java.util.Map<String, String> overrides) {
        setProfile("custom", overrides);
    }

    public void setProfile(String source, java.util.Map<String, String> overrides) {
        if ("preset".equals(source)) {
            profileOverrides = Collections.unmodifiableMap(presetProfile());
            return;
        }
        profileOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    public List<MarketAppInfo> search(String keyword, int page) throws IOException {
        LinkedHashMap<String, String> parameters = searchParameters(keyword, page);
        String url = XiaomiApiSigner.signedGet(MARKET_API + "search?", parameters);
        JSONObject response = getJson(url);
        return parseApps(findList(response));
    }

    public List<UpdateInfo> loadUpdates(List<InstalledPackageInfo> installedPackages)
            throws IOException {
        if (installedPackages.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, InstalledPackageInfo> installedByPackage = new LinkedHashMap<>();
        for (InstalledPackageInfo installed : installedPackages) {
            installedByPackage.put(installed.getPackageName(), installed);
        }
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.put("apkSource", joinInstalled(installedPackages, InstalledPackageInfo::getApkSource));
        parameters.put("autoUpdateEnabled", "false");
        parameters.put("background", "false");
        parameters.put("invalidSystemPackageHash", "null");
        parameters.put("showUnfitnessApp", "true");
        parameters.put("session_id", INSTANCE_ID + System.currentTimeMillis());
        parameters.put("splits", joinInstalled(installedPackages, InstalledPackageInfo::getSplits));
        parameters.put("packageName", joinInstalled(installedPackages, InstalledPackageInfo::getPackageName));
        parameters.put("versionCode", joinInstalled(installedPackages, item ->
                String.valueOf(item.getVersionCode())));
        parameters.put("oldApkHash", joinInstalled(installedPackages, InstalledPackageInfo::getOldApkHash));
        parameters.put("installedByMarket", joinInstalled(
                installedPackages, InstalledPackageInfo::getInstalledByMarket));
        parameters.put("ref", "update");
        XiaomiApiSigner.SignedPostRequest request =
                XiaomiApiSigner.signedPost(UPDATE_API, parameters);
        JSONObject response = postJson(request.getUrl(), request.getParameters());
        return parseUpdates(response, installedByPackage);
    }

    public List<UpdateInfo> loadManualUpdate(String packageName, long versionCode)
            throws IOException {
        if (packageName == null || packageName.isEmpty()) {
            throw new IOException("请输入有效的包名");
        }
        if (versionCode <= 0) {
            throw new IOException("请输入有效的版本号");
        }
        InstalledPackageInfo installed = new InstalledPackageInfo(
                packageName, "", versionCode, false, "0", "0", "0", "0");
        return loadUpdates(Collections.singletonList(installed));
    }

    public TodayFeedPage loadToday(int page) throws IOException {
        TodayFeedPage feed = null;
        TodayFeedPage goldMi = null;
        IOException feedFailure = null;
        IOException goldMiFailure = null;
        try {
            feed = loadTodayFeed(page);
        } catch (IOException exception) {
            feedFailure = exception;
            Log.w(TAG, "today feed unavailable", exception);
        }
        try {
            goldMi = loadGoldMiFeed(page);
        } catch (IOException exception) {
            goldMiFailure = exception;
            Log.w(TAG, "goldMi feed unavailable", exception);
        }
        if (feed == null && goldMi == null) {
            throw combinedTodayFailure(feedFailure, goldMiFailure);
        }
        return interleaveTodayFeeds(
                feed == null ? emptyTodayPage() : feed,
                goldMi == null ? emptyTodayPage() : goldMi);
    }

    private TodayFeedPage loadTodayFeed(int page) throws IOException {
        LinkedHashMap<String, String> parameters = todayParameters();
        parameters.put("page", String.valueOf(page));
        String url = XiaomiApiSigner.signedGet(MARKET_API + "today?", parameters);
        return parseTodayFeed(getJson(url));
    }

    private TodayFeedPage loadGoldMiFeed(int page) throws IOException {
        LinkedHashMap<String, String> parameters = todayParameters();
        parameters.put("page", String.valueOf(page));
        parameters.put("pageSize", String.valueOf(GOLD_MI_PAGE_SIZE));
        parameters.put("ref", "goldmi");
        parameters.put("previousFromRef", "goldmi");
        String url = XiaomiApiSigner.signedGet(MARKET_API + "zone/goldMiV2?", parameters);
        return parseGoldMiFeed(getJson(url));
    }

    private LinkedHashMap<String, String> todayParameters() {
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.put("bottomTab", "true");
        parameters.put("feReload", "false");
        parameters.put("isNewUI", "true");
        parameters.put("native", "1");
        parameters.put("suggestV", "1");
        parameters.put("supportSlide", "1");
        parameters.put("minacompatible", "1");
        parameters.put("pageRef", "com.xiaomi.market");
        parameters.put("sourcePackage", "com.xiaomi.market");
        parameters.put("previousFromRef", "today");
        return parameters;
    }

    public TodayArticle loadTodayArticle(String resourceId) throws IOException {
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.put("bottomTab", "true");
        parameters.put("feReload", "false");
        parameters.put("isNewUI", "true");
        parameters.put("native", "1");
        parameters.put("suggestV", "1");
        parameters.put("supportSlide", "1");
        parameters.put("minacompatible", "1");
        parameters.put("pageRef", "com.xiaomi.market");
        parameters.put("sourcePackage", "com.xiaomi.market");
        parameters.put("previousFromRef", "today");
        parameters.put("rId", resourceId);
        String url = XiaomiApiSigner.signedGet(MARKET_API + "topic/detail?", parameters);
        return parseTodayArticle(resourceId, getJson(url));
    }

    public MarketAppInfo findByPackageName(String packageName) throws IOException {
        IOException lastFailure = null;
        for (String query : packageQueries(packageName)) {
            try {
                MarketAppInfo match = search(query, 1).stream()
                        .filter(app -> packageName.equals(app.getPackageName()))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    return match;
                }
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IOException("Xiaomi API 未找到包名 " + packageName);
    }

    public MarketAppDetails loadDetail(MarketAppInfo app) throws IOException {
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.put("appId", String.valueOf(app.getAppId()));
        parameters.put("packageName", app.getPackageName());
        parameters.put("ref", "detail");
        parameters.put("pageTag", "detail");
        parameters.put("callerPackage", "com.xiaomi.market");
        String url = XiaomiApiSigner.signedGet(
                MARKET_API + "app/tabs/basicInfo/" + app.getAppId() + "?", parameters);
        JSONObject response = getJson(url);
        return XiaomiDetailParser.parseDetails(response, parseApp(findObject(response)));
    }

    public DownloadMetadata loadDownloadMetadata(MarketAppInfo app) throws IOException {
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.put("appId", String.valueOf(app.getAppId()));
        parameters.put("packageName", app.getPackageName());
        parameters.put("pName", app.getPackageName());
        parameters.put("supportModifyUrl", "true");
        parameters.put("supportSdm", "true");
        parameters.put("supportSpeedInstall", "true");
        String url = XiaomiApiSigner.signedGet(
                MARKET_API + "download/" + app.getAppId() + "?", parameters);
        return parseDownload(app, getJson(url));
    }

    private LinkedHashMap<String, String> baseParameters() {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        addClientParameters(parameters);
        addFeatureParameters(parameters);
        parameters.putAll(profileOverrides);
        return parameters;
    }

    private void addClientParameters(LinkedHashMap<String, String> parameters) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        String locale = Locale.getDefault().getLanguage();
        String country = Locale.getDefault().getCountry();
        String resolution = orderedResolution(metrics.widthPixels, metrics.heightPixels);
        String osIncremental = nonBlank(
                systemProperty("ro.mi.os.version.incremental"), Build.VERSION.INCREMENTAL);
        String miOsName = nonBlank(systemProperty("ro.mi.os.version.name"), "OS3.0");
        String miOsCode = nonBlank(systemProperty("ro.mi.os.version.code"), "3");
        String miuiCode = nonBlank(systemProperty("ro.miui.ui.version.code"), "816");
        String miuiName = nonBlank(systemProperty("ro.miui.ui.version.name"), "V816");
        String device = nonBlank(Build.DEVICE, "haotian");
        String model = nonBlank(Build.MODEL, "2410DPN6CC");
        String buildId = nonBlank(Build.ID, "BP2A.250605.031.A3");
        String region = nonBlank(systemProperty("ro.miui.region"), "CN");
        parameters.put("activedTimeInterval", "1");
        parameters.put("co", nonBlank(country, "CN"));
        parameters.put("cpuArchitecture", supportedArchitectures());
        parameters.put("device", device);
        parameters.put("deviceType", "0");
        parameters.put("installDay", "1");
        parameters.put("instance_id", INSTANCE_ID);
        parameters.put("la", locale.isEmpty() ? "zh" : locale);
        parameters.put("launchDay", "1");
        parameters.put("lo", region);
        parameters.put("marketVersion", "40008341");
        parameters.put("miuiBigVersionCode", miuiCode);
        parameters.put("miuiBigVersionName", miuiName);
        parameters.put("model", model);
        parameters.put("network", "unknown");
        parameters.put("newUser", "false");
        parameters.put("os", nonBlank(Build.VERSION.INCREMENTAL, osIncremental));
        parameters.put("osV2", osIncremental);
        parameters.put("osBigVersionCode", miOsCode);
        parameters.put("osBigVersionName", miOsName);
        parameters.put("androidVersion", Build.VERSION.RELEASE);
        parameters.put("pageConfigVersion", "18432101");
        parameters.put("resolution", resolution);
        parameters.put("densityDpi", String.valueOf(metrics.densityDpi));
        parameters.put("densityScaleFactor", String.valueOf(metrics.density));
        parameters.put("hybridFrameworkVersion", "13170201");
        parameters.put("buildId", buildId);
        parameters.put("supportedIslandVersion", supportedIslandVersion());
        parameters.put("hasGMSCore", hasGmsCore());
        parameters.put("ro", "unknown");
        parameters.put("sdk", String.valueOf(Build.VERSION.SDK_INT));
        parameters.put("webResVersion", "3193");
        parameters.put("oaId", "6f24320b1e9596bf");
    }

    private String orderedResolution(int width, int height) {
        return Math.min(width, height) + "*" + Math.max(width, height);
    }

    private String supportedArchitectures() {
        String architectures = String.join(",", Build.SUPPORTED_ABIS);
        return nonBlank(architectures, "arm64-v8a");
    }

    private String supportedIslandVersion() {
        if (context == null) return "1";
        String protocol = Settings.System.getString(
                context.getContentResolver(), "notification_focus_protocol");
        return protocol == null || protocol.isBlank() ? "1" : protocol;
    }

    private String hasGmsCore() {
        return String.valueOf("1".equals(systemProperty("ro.miui.has_gmscore")));
    }

    private String systemProperty(String key) {
        try {
            Method get = Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("get", String.class);
            return String.valueOf(get.invoke(null, key));
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void addFeatureParameters(LinkedHashMap<String, String> parameters) {
        parameters.put("ARCoreApkVersion", "-1");
        parameters.put("childMode", "0");
        parameters.put("clientConfigVersion", "447");
        parameters.put("clientFlag", "2");
        parameters.put("debugMode", "false");
        parameters.put("downloadRestriction", "1");
        parameters.put("downloadRestrictionMode", "0");
        parameters.put("isMiuiLite", "false");
        parameters.put("isSupportIsland", "true");
        parameters.put("isSupportMessageBox", "true");
        parameters.put("isSupportQuickGameInstall", "true");
        parameters.put("isSupportUninstall", "true");
        parameters.put("isTangoEnabled", "true");
        parameters.put("minorsMode", "false");
        parameters.put("needBlockWelfare", "true");
        parameters.put("privacyCompliance", "true");
        parameters.put("rankTypeV2", "true");
        parameters.put("rustRuntimeVersion", "1.6.0");
        parameters.put("supportAgent", "true");
        parameters.put("supportBundle", "1");
        parameters.put("supportDownloaderUpdate", "1");
        parameters.put("supportOperateIcon", "true");
        parameters.put("supportPatchVer", "0,1,2,3");
        parameters.put("supportSmallApk", "true");
        parameters.put("useExpId", "2023001,2311551,2398789,2398846,2346056,2075312,2362289,2378691,2403435,2263575,2404589,2333160,2286434,1411227,1978999,2368587,2059056");
    }

    private LinkedHashMap<String, String> presetProfile() {
        LinkedHashMap<String, String> profile = new LinkedHashMap<>();
        profile.put("co", "CN");
        profile.put("la", "zh");
        profile.put("lo", "CN");
        profile.put("cpuArchitecture", "arm64-v8a");
        profile.put("device", "popsicle");
        profile.put("model", "2509FPN0BC");
        profile.put("os", "OS3.0.315.0.WPBCNXM");
        profile.put("osV2", "OS3.0.315.0.WPBCNXM");
        profile.put("androidVersion", "15");
        profile.put("sdk", "35");
        profile.put("resolution", "1080*2400");
        profile.put("densityDpi", "420");
        profile.put("densityScaleFactor", "2.625");
        profile.put("miuiBigVersionCode", "816");
        profile.put("miuiBigVersionName", "V816");
        profile.put("osBigVersionCode", "3");
        profile.put("osBigVersionName", "OS3.0");
        profile.put("marketVersion", "40008341");
        profile.put("pageConfigVersion", "18411801");
        profile.put("webResVersion", "3211");
        profile.put("hybridFrameworkVersion", "13180003");
        profile.put("buildId", "BP2A.250605.031.A3");
        profile.put("hasGMSCore", "true");
        profile.put("supportedIslandVersion", "3");
        return profile;
    }

    private LinkedHashMap<String, String> searchParameters(String keyword, int page) {
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.put("bottomTab", "true");
        parameters.put("flag", "2");
        parameters.put("feReload", "false");
        parameters.put("isNewUI", "true");
        parameters.put("ref", "input");
        parameters.put("responseType", "1");
        parameters.put("searchFrom", "input");
        parameters.put("minacompatible", "1");
        parameters.put("native", "1");
        parameters.put("renderType", "1");
        parameters.put("pageRef", "com.miui.home");
        parameters.put("showGoogleAppsType", "2");
        parameters.put("sourcePackage", "com.miui.home");
        parameters.put("supportSlide", "1");
        parameters.put("previousFromRef", "searchSuggest");
        parameters.put("voiceAssistVersion", "507513001");
        parameters.put("isSupportCreative", "true");
        parameters.put("refs", "input-searchResult");
        parameters.put("suggestV", "1");
        parameters.put("keyword", keyword);
        parameters.put("page", String.valueOf(page));
        parameters.put("search_session", INSTANCE_ID + System.currentTimeMillis());
        return parameters;
    }

    private TodayFeedPage parseTodayFeed(JSONObject response) throws IOException {
        JSONArray groups = response.optJSONArray("list");
        if (groups == null) {
            throw new IOException("Xiaomi API today response has no list");
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

    private void appendPrimaryItem(JSONObject data, List<TodayFeaturedItem> items)
            throws IOException {
        if (data == null) return;
        TodayFeaturedItem parsed = parseTodayFeaturedItem(data);
        if (parsed != null) items.add(parsed);
    }

    private TodayFeedPage parseGoldMiFeed(JSONObject response) throws IOException {
        JSONArray groups = response.optJSONArray("list");
        if (groups == null) {
            throw new IOException("Xiaomi API goldMi response has no list");
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

    private TodayFeaturedItem parseTodayFeaturedItem(JSONObject item) throws IOException {
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

    private TodayFeaturedItem parseGoldMiItem(JSONObject data, JSONObject item)
            throws IOException {
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

    private TodayFeedPage interleaveTodayFeeds(TodayFeedPage primary, TodayFeedPage goldMi) {
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

    private IOException combinedTodayFailure(IOException feedFailure, IOException goldMiFailure) {
        String primaryMessage = failureMessage(feedFailure, "主今日接口失败");
        String goldMiMessage = failureMessage(goldMiFailure, "金米奖接口失败");
        return new IOException("今日内容加载失败：" + primaryMessage + "；" + goldMiMessage, feedFailure);
    }

    private String failureMessage(IOException failure, String fallback) {
        return failure == null || failure.getMessage() == null
                ? fallback : failure.getMessage();
    }

    private TodayFeedPage emptyTodayPage() {
        return new TodayFeedPage(Collections.emptyList(), false);
    }

    private List<MarketAppInfo> parseTodayApps(JSONObject item) throws IOException {
        List<MarketAppInfo> apps = new ArrayList<>();
        MarketAppInfo directApp = parseOptionalApp(item);
        if (directApp != null) {
            apps.add(directApp);
        }
        appendAppList(item.optJSONArray("listApp"), apps);
        appendAppList(item.optJSONArray("appList"), apps);
        return uniqueApps(apps);
    }

    private List<TodayFeaturedItem> uniqueTodayItems(List<TodayFeaturedItem> items) {
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

    private String todayItemKey(TodayFeaturedItem item, int index) {
        if (!item.getResourceId().isEmpty()) return "resource:" + item.getResourceId();
        if (!item.getClickUrl().isEmpty()) return "url:" + item.getClickUrl();
        if (item.getApp() != null) return "app:" + item.getApp().getPackageName();
        if (!item.getTitle().isEmpty()) return "title:" + item.getTitle();
        return "position:" + index;
    }

    private TodayArticle parseTodayArticle(String resourceId, JSONObject response) throws IOException {
        JSONObject data = firstArticleData(response);
        JSONArray blocks = data.optJSONArray("topicItemList");
        if (blocks == null) {
            throw new IOException("Xiaomi API article response has no topicItemList");
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
            parseArticleBlock(block, body, imageUrls, apps);
        }
        return new TodayArticle(
                resourceId,
                firstText(data, "title", "detailTitle", "outerTitle", "webViewTitle"),
                normalizeImage(headerImage, "l720q90"),
                body.toString(),
                imageUrls,
                uniqueApps(apps));
    }

    private JSONObject firstArticleData(JSONObject response) throws IOException {
        JSONArray list = response.optJSONArray("list");
        JSONObject first = list == null ? null : list.optJSONObject(0);
        JSONObject data = first == null ? null : first.optJSONObject("data");
        if (data == null) {
            throw new IOException("Xiaomi API article response has no data");
        }
        return data;
    }

    private void parseArticleBlock(
            JSONObject block,
            StringBuilder body,
            List<String> imageUrls,
            List<MarketAppInfo> apps) throws IOException {
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

    private void appendAppList(JSONArray list, List<MarketAppInfo> apps) throws IOException {
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

    private void appendBody(StringBuilder body, String text) {
        if (text.isEmpty()) {
            return;
        }
        if (body.length() > 0) {
            body.append("<br><br>");
        }
        body.append(text);
    }

    private void appendRichTextImages(String html, List<String> imageUrls) {
        Matcher matcher = IMAGE_SOURCE_PATTERN.matcher(html);
        while (matcher.find()) {
            addImageUrl(imageUrls, normalizeImage(matcher.group(1), "q90"));
        }
    }

    private void addImageUrl(List<String> imageUrls, String imageUrl) {
        if (!imageUrl.isEmpty() && !imageUrls.contains(imageUrl)) {
            imageUrls.add(imageUrl);
        }
    }

    private List<MarketAppInfo> uniqueApps(List<MarketAppInfo> apps) {
        Set<String> packages = new LinkedHashSet<>();
        List<MarketAppInfo> unique = new ArrayList<>();
        for (MarketAppInfo app : apps) {
            if (packages.add(app.getPackageName())) {
                unique.add(app);
            }
        }
        return unique;
    }

    private MarketAppInfo parseOptionalApp(JSONObject source) throws IOException {
        JSONObject app = source.optJSONObject("appInfo");
        JSONObject candidate = app == null ? source : app;
        if (candidate.optString("packageName", "").isEmpty()) {
            return null;
        }
        return parseApp(candidate);
    }

    private String resourceIdFromUrl(String url) {
        int marker = url.indexOf("rId=");
        if (marker < 0) {
            return "";
        }
        String value = url.substring(marker + 4);
        int end = value.indexOf('&');
        return decodeResourceId(end < 0 ? value : value.substring(0, end));
    }

    private String decodeResourceId(String value) {
        if (value.isEmpty()) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is unavailable", exception);
        }
    }


    private Set<String> packageQueries(String packageName) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(packageName);
        String[] segments = packageName.split("\\.");
        for (int index = segments.length - 1; index >= 0; index--) {
            if (segments[index].length() >= 3) {
                queries.add(segments[index]);
            }
        }
        String lastSegment = segments[segments.length - 1];
        if (lastSegment.length() > 2) {
            queries.add(lastSegment.substring(lastSegment.length() - 2));
        }
        return queries;
    }

    private JSONObject getJson(String url) throws IOException {
        HttpURLConnection connection = open(url, "GET");
        try {
            int status = connection.getResponseCode();
            String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                throw new IOException("Xiaomi API HTTP " + status + ": " + body);
            }
            return new JSONObject(body);
        } catch (JSONException exception) {
            throw new IOException("Xiaomi API returned invalid JSON", exception);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject postJson(String url, java.util.Map<String, String> parameters)
            throws IOException {
        HttpURLConnection connection = open(url, "POST");
        byte[] body = XiaomiApiSigner.formBody(parameters).getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setFixedLengthStreamingMode(body.length);
        try (java.io.OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        try {
            int status = connection.getResponseCode();
            String responseBody = readBody(
                    status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                throw new IOException("Xiaomi API HTTP " + status + ": " + responseBody);
            }
            return new JSONObject(responseBody);
        } catch (JSONException exception) {
            throw new IOException("Xiaomi API returned invalid JSON", exception);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("x-version-name", "4.120.1");
        connection.setRequestProperty("x-version-code", "40007441");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private JSONArray findList(JSONObject response) throws IOException {
        JSONObject data = response.optJSONObject("data");
        JSONArray list = response.optJSONArray("list");
        if (list == null && data != null) {
            list = data.optJSONArray("listApp");
        }
        if (list == null && data != null) {
            list = data.optJSONArray("appList");
        }
        if (list == null) {
            throw new IOException("Xiaomi API search response has no app list");
        }
        return list;
    }

    private JSONObject findObject(JSONObject response) throws IOException {
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

    private List<MarketAppInfo> parseApps(JSONArray list) throws IOException {
        List<MarketAppInfo> apps = new ArrayList<>();
        for (int index = 0; index < list.length(); index++) {
            try {
                appendSearchApps(apps, list.getJSONObject(index));
            } catch (JSONException exception) {
                throw new IOException("Xiaomi API app item is invalid at index " + index, exception);
            }
        }
        return apps;
    }

    private List<UpdateInfo> parseUpdates(
            JSONObject response,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage) throws IOException {
        List<UpdateInfo> updates = new ArrayList<>();
        appendUpdates(response.optJSONArray("miuiApp"), installedByPackage, updates);
        appendUpdates(response.optJSONArray("listApp"), installedByPackage, updates);
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            appendUpdates(data.optJSONArray("miuiApp"), installedByPackage, updates);
            appendUpdates(data.optJSONArray("listApp"), installedByPackage, updates);
        }
        return uniqueUpdates(updates);
    }

    private void appendUpdates(
            JSONArray items,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage,
            List<UpdateInfo> updates) throws IOException {
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

    private UpdateInfo parseUpdate(
            JSONObject item,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage) throws IOException {
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

    private MarketAppInfo parseUpdateApp(JSONObject candidate, long installedVersionCode)
            throws IOException {
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

    private JSONObject selectFitnessApk(JSONObject candidate, long installedVersionCode) {
        JSONObject fitnessApks = candidate.optJSONObject("fitnessApks");
        JSONArray apks = fitnessApks == null ? null : fitnessApks.optJSONArray("apks");
        if (apks == null) {
            return null;
        }
        JSONObject matching = highestApk(apks, installedVersionCode, true);
        return matching == null ? highestApk(apks, installedVersionCode, false) : matching;
    }

    private JSONObject highestApk(JSONArray apks, long installedVersionCode, boolean enforceRange) {
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

    private boolean matchesVersionRange(JSONObject apk, long installedVersionCode) {
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

    private List<UpdateInfo> uniqueUpdates(List<UpdateInfo> updates) {
        Set<String> packages = new LinkedHashSet<>();
        List<UpdateInfo> unique = new ArrayList<>();
        for (UpdateInfo update : updates) {
            if (packages.add(update.getApp().getPackageName())) {
                unique.add(update);
            }
        }
        return unique;
    }

    private void appendSearchApps(List<MarketAppInfo> apps, JSONObject item) throws IOException {
        JSONObject data = item.optJSONObject("data");
        JSONArray nested = data == null ? null : data.optJSONArray("listApp");
        if (nested == null && data != null) {
            nested = data.optJSONArray("appList");
        }
        if (nested == null) {
            apps.add(parseApp(unwrapSearchItem(item)));
            return;
        }
        for (int index = 0; index < nested.length(); index++) {
            try {
                apps.add(parseApp(unwrapSearchItem(nested.getJSONObject(index))));
            } catch (JSONException exception) {
                throw new IOException("Xiaomi API nested app item is invalid at index " + index, exception);
            }
        }
    }

    private JSONObject unwrapSearchItem(JSONObject item) {
        JSONObject data = item.optJSONObject("data");
        if (data == null) {
            return item;
        }
        JSONObject appInfo = data.optJSONObject("appInfo");
        return appInfo == null ? data : appInfo;
    }

    private JSONObject unwrapUpdateItem(JSONObject item) {
        JSONObject data = item.optJSONObject("data");
        if (data == null) {
            return item;
        }
        JSONObject appInfo = data.optJSONObject("appInfo");
        return appInfo == null ? data : appInfo;
    }

    private String joinInstalled(
            List<InstalledPackageInfo> installedPackages,
            Function<InstalledPackageInfo, String> selector) {
        List<String> values = new ArrayList<>(installedPackages.size());
        for (InstalledPackageInfo installed : installedPackages) {
            values.add(selector.apply(installed));
        }
        return String.join(",", values);
    }

    private MarketAppInfo.Builder copyApp(MarketAppInfo app) {
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

    private MarketAppInfo parseApp(JSONObject json) throws IOException {
        String packageName = json.optString("packageName", "");
        if (packageName.isEmpty()) {
            throw new IOException("Xiaomi API app has no packageName");
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
                .quickApp(json.optBoolean("isQuickApp", json.optBoolean("quickApp", false)))
                .reservationApp(json.optBoolean(
                        "isReservationApp", json.optBoolean("reservationApp", false)))
                .introduction(firstText(json, "briefShow", "introduction", "description"))
                .downloadCount(json.optLong("downloadCount", 0))
                .commentCount(XiaomiDetailParser.firstLong(json, "ratingTotalCount", "commentCount", "totalCommentCount", "commentNum"))
                .ageClassification(json.optString("ageClassification", ""))
                .updateTime(json.optLong("updateTime", 0))
                .registrationNumber(json.optString("registrationNum", ""))
                .screenshotUrls(XiaomiDetailParser.parseScreenshotUrls(json))
                .build();
    }

    private DownloadMetadata parseDownload(MarketAppInfo app, JSONObject response) throws IOException {
        JSONArray apks = response.optJSONArray("apks");
        if (apks == null) {
            JSONObject data = response.optJSONObject("data");
            apks = data == null ? null : data.optJSONArray("apks");
        }
        if (apks == null) {
            throw new IOException("Xiaomi API download response has no apks");
        }
        List<ApkArtifact> artifacts = parseArtifacts(apks);
        long totalSize = artifacts.stream().mapToLong(ApkArtifact::getSize).sum();
        return new DownloadMetadata(app, artifacts, totalSize,
                response.optString("versionName", app.getVersionName()),
                response.optLong("versionCode", app.getVersionCode()));
    }

    private List<ApkArtifact> parseArtifacts(JSONArray apks) throws IOException {
        List<ApkArtifact> artifacts = new ArrayList<>();
        for (int index = 0; index < apks.length(); index++) {
            try {
                JSONObject item = apks.getJSONObject(index);
                String url = firstText(item, "url", "fullUrl");
                if (url.isEmpty()) {
                    throw new IOException("Xiaomi API artifact has no url at index " + index);
                }
                String name = item.optString("name", "base.apk");
                String type = item.optString("type", "base");
                String diffUrl = firstText(item, "diffUrl", "patchUrl");
                long diffSize = item.optLong("diffSize", item.optLong("patchSize", 0));
                String diffHash = firstText(item, "diffHash", "patchChecksum");
                int diffVersion = item.optInt("patchVersion", 3);
                artifacts.add(new ApkArtifact(
                        name,
                        type,
                        normalizeDownload(url),
                        item.optLong("size", 0),
                        firstText(item, "hash", "checksum"),
                        normalizeDownload(diffUrl),
                        diffSize,
                        diffHash,
                        diffVersion,
                        item.optString("baseApkPath", "")));
            } catch (JSONException exception) {
                throw new IOException("Xiaomi API artifact is invalid at index " + index, exception);
            }
        }
        return artifacts;
    }

    private String firstText(JSONObject json, String... keys) {
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

    private String firstUsableImage(JSONObject json, String... keys) {
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

    private boolean isThumbnailBaseUrl(String value) {
        String normalized = value.trim().replace("\\/", "/");
        return normalized.equalsIgnoreCase(THUMBNAIL_BASE_URL)
                || normalized.equalsIgnoreCase(THUMBNAIL_BASE_URL.substring(0, THUMBNAIL_BASE_URL.length() - 1));
    }

    private String todayClickUrl(JSONObject item) {
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

    private String normalizeImage(String value) {
        return normalizeImage(value, "l144q80");
    }

    private String normalizeImage(String value, String size) {
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
            return "https://sf0.market.xiaomi.com/thumbnail/" + value;
        }
        return "https://sf0.market.xiaomi.com/thumbnail/webp/" + size + "/" + value;
    }

    private String upgradeMarketImage(String value) {
        String host = value.substring("http://".length()).split("/", 2)[0].toLowerCase(Locale.ROOT);
        if (host.equals("market.xiaomi.com") || host.endsWith(".market.xiaomi.com")) {
            return "https://" + value.substring("http://".length());
        }
        return value;
    }

    private String normalizeDownload(String value) {
        if (value.isEmpty()) {
            return value;
        }
        if (value.startsWith("http")) {
            return value;
        }
        return DOWNLOAD_API + value;
    }
}
