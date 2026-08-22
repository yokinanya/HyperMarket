package com.hyper.market.api;

import com.hyper.market.model.ApkArtifact;
import com.hyper.market.model.DownloadMetadata;
import com.hyper.market.model.InstalledPackageInfo;
import com.hyper.market.model.MarketAppDetails;
import com.hyper.market.model.MarketAppInfo;
import com.hyper.market.model.SearchFeedPage;
import com.hyper.market.model.TodayArticle;
import com.hyper.market.model.TodayFeedPage;
import com.hyper.market.model.TodayFeaturedItem;
import com.hyper.market.model.UpdateInfo;

import android.os.Build;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int GOLD_MI_PAGE_SIZE = 9;
    private static final String TAG = "XiaomiApi";
    private static final String MARKET_PACKAGE = "com.xiaomi.market";
    private static final String MARKET_VERSION_NAME = "4.120.1";
    private static final String MARKET_VERSION_CODE = "40007441";
    private static final String MARKET_QUERY_VERSION = "40008341";
    private static final String PAGE_CONFIG_VERSION = "18411801";
    private static final String WEB_RES_VERSION = "3211";
    private static final String DEFAULT_EXPERIMENT_IDS =
            "2023001,2311551,2398789,2398846,2346056,2075312,2362289,2378691,"
                    + "2403435,2263575,2404589,2333160,2286434,1411227,1978999,"
                    + "2368587,2362073,2059056";
    private static final String DEFAULT_XMSF_VERSION = "70005022";
    private static final long OPEN_ENDED_APP_VERSION = 2_147_483_647L;
    private static final String QUICK_GAME_TYPE = "quickGame";
    private static final String INSTANCE_ID_KEY = "market_instance_id";
    private static final String SETTINGS_FILE = "market_settings";
    private static final Pattern IMAGE_SOURCE_PATTERN = Pattern.compile(
            "(?i)<img\\b[^>]*(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"'][^>]*>");
    private volatile java.util.Map<String, String> profileOverrides = Collections.emptyMap();
    private final Context context;
    private final String instanceId;
    private final XiaomiDeviceIdentity deviceIdentity;
    private final Object identityBootstrapLock = new Object();
    private final Map<String, String> oldApkHashCache = new ConcurrentHashMap<>();

    public XiaomiApiClient() {
        this(null);
    }

    public XiaomiApiClient(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        this.instanceId = loadInstanceId(this.context);
        this.deviceIdentity = this.context == null ? null : new XiaomiDeviceIdentity(this.context);
    }

    public void setProfileOverrides(java.util.Map<String, String> overrides) {
        setProfile("custom", overrides);
    }

    public void setProfile(String source, java.util.Map<String, String> overrides) {
        if ("preset".equals(source)) {
            profileOverrides = Collections.emptyMap();
            return;
        }
        if ("device".equals(source)) {
            profileOverrides = Collections.emptyMap();
            return;
        }
        profileOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    public SearchFeedPage search(String keyword, int page) throws IOException {
        if (page == 0) ensureServerIdentity();
        LinkedHashMap<String, String> parameters = searchParameters(keyword, page);
        Log.i(TAG, "search request keyword=" + keyword
                + " page=" + page
                + " marketVersion=" + parameters.get("marketVersion")
                + " installDay=" + parameters.get("installDay")
                + " instance=" + parameters.get("instance_id")
                + " keyCount=" + parameters.size());
        String url = signedGet(MARKET_API + "search?", parameters);
        JSONObject response = getJson(url);
        throwIfSearchRejected(response);
        JSONArray list = findSearchList(response);
        List<MarketAppInfo> apps = list == null
                ? Collections.emptyList() : parseSearchApps(list);
        boolean hasMore = response.optBoolean("hasMore", !apps.isEmpty());
        Log.i(TAG, "search keyword=" + keyword
                + " page=" + page
                + " code=" + response.optString("code", "")
                + " message=" + response.optString("message", "")
                + " apps=" + apps.size()
                + " hasMore=" + hasMore
                + " marketVersion=" + parameters.get("marketVersion")
                + " dctx=" + parameters.containsKey("dctx"));
        return new SearchFeedPage(apps, hasMore);
    }

    private void throwIfSearchRejected(JSONObject response) throws IOException {
        String code = response.optString("code", "");
        String message = response.optString("message", "");
        if ("-1".equals(code) && !isBlank(message)) {
            Log.w(TAG, "search rejected code=" + code + " message=" + message);
            throw new IOException("Xiaomi API search rejected: " + message);
        }
    }

    private void ensureServerIdentity() throws IOException {
        if (deviceIdentity == null || !isBlank(deviceIdentity.read().dctx)) return;
        synchronized (identityBootstrapLock) {
            if (!isBlank(deviceIdentity.read().dctx)) return;
            JSONObject response = loadServerIdentity();
            String dctx = response.optString("dctx", "");
            String experimentIds = response.optString("exp_id", "");
            if (isBlank(experimentIds)) experimentIds = response.optString("expId", "");
            if (isBlank(dctx)) {
                throw new IOException("Xiaomi API expId response has no dctx: "
                        + response.optString("message", "unknown response"));
            }
            deviceIdentity.storeServerIdentity(dctx, experimentIds);
            Log.i(TAG, "server identity initialized, expIds=" + !isBlank(experimentIds));
        }
    }

    private JSONObject loadServerIdentity() throws IOException {
        LinkedHashMap<String, String> parameters = baseParameters();
        removeOfficialOnlyParameters(parameters);
        XiaomiDeviceIdentity.Identity identity = deviceIdentity.read();
        parameters.put("xmsfVersion", nonBlank(identity.xmsfVersion, DEFAULT_XMSF_VERSION));
        String url = signedGet(MARKET_API + "expId?", parameters);
        return getJson(url);
    }

    public List<UpdateInfo> loadUpdates(List<InstalledPackageInfo> installedPackages)
            throws IOException {
        if (installedPackages.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, InstalledPackageInfo> installedByPackage = new LinkedHashMap<>();
        for (InstalledPackageInfo installed : installedPackages) {
            installedByPackage.put(installed.getPackageName(), installed);
        }
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.remove("tzNonce");
        parameters.remove("tzSign");
        parameters.put("apkSource", joinInstalled(installedPackages, InstalledPackageInfo::getApkSource));
        parameters.put("autoUpdateEnabled", "false");
        parameters.put("background", "false");
        parameters.put("invalidSystemPackageHash", "null");
        parameters.put("showUnfitnessApp", "true");
        parameters.put("session_id", instanceId + System.currentTimeMillis());
        parameters.put("splits", joinInstalled(installedPackages, InstalledPackageInfo::getSplits));
        parameters.put("packageName", joinInstalled(installedPackages, InstalledPackageInfo::getPackageName));
        parameters.put("versionCode", joinInstalled(installedPackages, item ->
                String.valueOf(item.getVersionCode())));
        parameters.put("oldApkHash", joinInstalled(installedPackages, InstalledPackageInfo::getOldApkHash));
        parameters.put("installedByMarket", joinInstalled(
                installedPackages, InstalledPackageInfo::getInstalledByMarket));
        parameters.put("ref", "update");
        XiaomiApiSigner.SignedPostRequest request = signedPost(UPDATE_API, parameters);
        JSONObject response = postJson(request.getUrl(), request.getParameters());
        List<UpdateInfo> updates = parseUpdates(response, installedByPackage);
        return withDiffSizes(updates);
    }

    private List<UpdateInfo> withDiffSizes(List<UpdateInfo> updates) {
        if (updates.isEmpty()) return updates;
        try {
            Map<String, Long> diffSizes = loadDiffSizes(updates);
            List<UpdateInfo> enriched = new ArrayList<>(updates.size());
            for (UpdateInfo update : updates) {
                long diffSize = diffSizes.getOrDefault(update.getApp().getPackageName(), 0L);
                enriched.add(diffSize > 0
                        ? new UpdateInfo(update.getApp(), update.getInstalledPackage(), diffSize)
                        : update);
            }
            return enriched;
        } catch (IOException exception) {
            Log.i(TAG, "diffsize unavailable: " + exception.getMessage());
            return updates;
        }
    }

    private Map<String, Long> loadDiffSizes(List<UpdateInfo> updates) throws IOException {
        LinkedHashMap<String, String> parameters = baseParameters();
        parameters.remove("tzNonce");
        parameters.remove("tzSign");
        parameters.put("session_id", instanceId + System.currentTimeMillis());
        parameters.put("packageName", joinUpdates(updates, item -> item.getApp().getPackageName()));
        parameters.put("versionCode", joinUpdates(updates,
                item -> String.valueOf(item.getInstalledPackage().getVersionCode())));
        parameters.put("oldApkHash", joinUpdates(updates,
                item -> oldApkHash(item.getInstalledPackage().getPackageName())));
        XiaomiApiSigner.SignedPostRequest request = signedPost(
                "https://updateinfo.market.xiaomi.com/apm/updateinfo/diffsize?lo=CN", parameters);
        return parseDiffSizes(postJson(request.getUrl(), request.getParameters()));
    }

    private Map<String, Long> parseDiffSizes(JSONObject response) {
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
            if (!isBlank(packageName) && size > 0) result.put(packageName, size);
        }
        return result;
    }

    private String joinUpdates(
            List<UpdateInfo> updates, Function<UpdateInfo, String> selector) {
        List<String> values = new ArrayList<>(updates.size());
        for (UpdateInfo update : updates) values.add(selector.apply(update));
        return String.join(",", values);
    }

    private String oldApkHash(String packageName) {
        if (context == null) return "0";
        try {
            String path = context.getPackageManager().getApplicationInfo(packageName, 0).sourceDir;
            java.io.File file = new java.io.File(path);
            String cacheKey = packageName + ":" + file.lastModified() + ":" + file.length();
            String cached = oldApkHashCache.get(cacheKey);
            if (!isBlank(cached)) return cached;
            try (FileInputStream input = new FileInputStream(path)) {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                String hash = hexDigest(digest.digest());
                oldApkHashCache.put(cacheKey, hash);
                return hash;
            }
        } catch (IOException | PackageManager.NameNotFoundException |
                 java.security.NoSuchAlgorithmException exception) {
            return "0";
        }
    }

    private String hexDigest(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
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
        String url = signedGet(MARKET_API + "today?", parameters);
        return parseTodayFeed(getJson(url));
    }

    private TodayFeedPage loadGoldMiFeed(int page) throws IOException {
        LinkedHashMap<String, String> parameters = todayParameters();
        parameters.put("page", String.valueOf(page));
        parameters.put("pageSize", String.valueOf(GOLD_MI_PAGE_SIZE));
        parameters.put("ref", "goldmi");
        parameters.put("previousFromRef", "goldmi");
        String url = signedGet(MARKET_API + "zone/goldMiV2?", parameters);
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
        String url = signedGet(MARKET_API + "topic/detail?", parameters);
        return parseTodayArticle(resourceId, getJson(url));
    }

    public MarketAppInfo findByPackageName(String packageName) throws IOException {
        IOException lastFailure = null;
        for (String query : packageQueries(packageName)) {
            try {
                MarketAppInfo match = search(query, 0).getApps().stream()
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
        parameters.put("bottomTab", "true");
        parameters.put("feReload", "false");
        parameters.put("isNewUI", "true");
        parameters.put("minacompatible", "1");
        parameters.put("native", "1");
        parameters.put("entrance", "detail");
        parameters.put("appId", String.valueOf(app.getAppId()));
        parameters.put("bundleType", "main");
        parameters.put("pageRef", "com.miui.home");
        parameters.put("originalPageRef", "com.miui.home");
        parameters.put("packageName", app.getPackageName());
        parameters.put("sourcePackage", MARKET_PACKAGE);
        parameters.put("senderPackageName", MARKET_PACKAGE);
        parameters.put("callerPackage", MARKET_PACKAGE);
        parameters.put("callerSignature", "88daa889de21a80bca64464243c9ede6");
        parameters.put("ref", "detail");
        parameters.put("pageTag", "detail");
        parameters.put("previousFromRef", "searchResult");
        parameters.put("suggestV", "1");
        parameters.put("supportSlide", "1");
        parameters.put("pos", "detailInstallBtn");
        parameters.put("sid", instanceId + "default");
        parameters.put("oldVersionCode", "0");
        parameters.put("releaseType", "0");
        parameters.put("refs", app.getAppId() + "input-searchResult-detail/");
        parameters.put("supportH5", "2");
        parameters.put("supportBetaApp", "true");
        parameters.put("supportGameLottery", "true");
        parameters.put("supportCorpInternal", "true");
        parameters.put("supportSmallApk", "true");
        parameters.put("safeModeCheck", "false");
        parameters.put("safeModeType", "0");
        parameters.put("gameCenterVersionCode", "135100100");
        parameters.put("minaPlatformVersion", "13170003");
        parameters.put("voiceAssistVersion", "507513001");
        parameters.put("personalAssistantVersion", "253081");
        String url = signedGet(
                MARKET_API + "app/tabs/basicInfo/" + app.getAppId() + "?", parameters);
        JSONObject response = getJson(url);
        return XiaomiDetailParser.parseDetails(response, parseApp(findObject(response)));
    }

    public DownloadMetadata loadDownloadMetadata(MarketAppInfo app) throws IOException {
        LinkedHashMap<String, String> parameters = baseParameters();
        PackageInfo installed = installedPackage(app.getPackageName());
        parameters.put("ad", "0");
        parameters.put("appId", String.valueOf(app.getAppId()));
        parameters.put("autoUpdateEnabled", "false");
        parameters.put("bundleType", "main");
        parameters.put("downloadGrantType", "0");
        parameters.put("kcgsdk", "false");
        parameters.put("lastUseTime", "0");
        parameters.put("oldApkHash", "0");
        parameters.put("packageName", app.getPackageName());
        parameters.put("pName", app.getPackageName());
        parameters.put("pageRef", "com.miui.home");
        parameters.put("ref", installed == null ? "detail" : "upgrade");
        parameters.put("refPosition", "0");
        parameters.put("sourcePackage", "com.miui.home");
        parameters.put("supportCloudProfile", "true");
        parameters.put("supportCloudVerify", "true");
        parameters.put("supportCompressType", "1");
        parameters.put("supportModifyUrl", "true");
        parameters.put("supportSdm", "true");
        parameters.put("supportSpeedInstall", "true");
        parameters.put("taskStartTime", String.valueOf(System.currentTimeMillis()));
        parameters.put("useCache", "false");
        parameters.put("versionCode", String.valueOf(installedVersionCode(installed)));
        parameters.put("versionName", installed == null || installed.versionName == null
                ? "" : installed.versionName);
        parameters.put("card_position", "0");
        parameters.put("item_position", "0");
        parameters.put("pre_rerank_card_position", "0");
        parameters.put("pre_rerank_item_position", "0");
        parameters.put("session_id", instanceId + System.currentTimeMillis());
        parameters.put("previousFromRef", "detailInstallBtn");
        String url = signedGet(MARKET_API + "download/" + app.getAppId() + "?", parameters);
        return parseDownload(app, getJson(url));
    }

    private PackageInfo installedPackage(String packageName) {
        if (context == null) return null;
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }
    }

    private long installedVersionCode(PackageInfo info) {
        if (info == null) return 0;
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private LinkedHashMap<String, String> baseParameters() {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        addClientParameters(parameters);
        addFeatureParameters(parameters);
        parameters.putAll(profileOverrides);
        parameters.remove("buildId");
        return parameters;
    }

    private String signedGet(String url, Map<String, String> parameters) {
        return XiaomiApiSigner.signedGet(url, parameters);
    }

    private XiaomiApiSigner.SignedPostRequest signedPost(
            String url, Map<String, String> parameters) {
        return XiaomiApiSigner.signedPost(url, parameters);
    }

    private void addClientParameters(LinkedHashMap<String, String> parameters) {
        DisplayMetrics metrics = context == null
                ? Resources.getSystem().getDisplayMetrics()
                : context.getResources().getDisplayMetrics();
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
        String region = nonBlank(systemProperty("ro.miui.region"), "CN");
        XiaomiDeviceIdentity.Identity identity = deviceIdentity == null
                ? null : deviceIdentity.read();
        parameters.put("activedTimeInterval", identity == null
                ? "1" : identity.activeTimeInterval);
        parameters.put("co", nonBlank(country, "CN"));
        parameters.put("cpuArchitecture", supportedArchitectures());
        parameters.put("device", device);
        parameters.put("deviceType", "0");
        parameters.put("installDay", identity == null ? "1" : identity.installDay);
        parameters.put("instance_id", instanceId);
        parameters.put("la", locale.isEmpty() ? "zh" : locale);
        parameters.put("launchDay", identity == null ? "1" : identity.installDay);
        parameters.put("lo", region);
        parameters.put("marketVersion", MARKET_QUERY_VERSION);
        parameters.put("miuiBigVersionCode", miuiCode);
        parameters.put("miuiBigVersionName", miuiName);
        parameters.put("model", model);
        parameters.put("network", "unknown");
        parameters.put("newUser", "false");
        parameters.put("os", nonBlank(osIncremental, Build.VERSION.RELEASE));
        parameters.put("osV2", nonBlank(osIncremental, Build.VERSION.RELEASE));
        parameters.put("osBigVersionCode", miOsCode);
        parameters.put("osBigVersionName", miOsName);
        parameters.put("androidVersion", Build.VERSION.RELEASE);
        parameters.put("customization", nonBlank(
                systemProperty("ro.miui.customized.region"), ""));
        parameters.put("pageConfigVersion", PAGE_CONFIG_VERSION);
        parameters.put("resolution", resolution);
        parameters.put("densityDpi", String.valueOf(metrics.densityDpi));
        parameters.put("densityScaleFactor", String.valueOf(metrics.density));
        parameters.put("hybridFrameworkVersion", hybridFrameworkVersion());
        parameters.put("minaPlatformVersion", hybridFrameworkVersion());
        parameters.put("recentSearchKey", "");
        parameters.put("gameCenterVersionCode", packageVersionCode("com.xiaomi.gamecenter"));
        parameters.put("supportedIslandVersion", supportedIslandVersion());
        parameters.put("hasGMSCore", hasGmsCore());
        parameters.put("ro", "unknown");
        parameters.put("sdk", String.valueOf(Build.VERSION.SDK_INT));
        parameters.put("webResVersion", WEB_RES_VERSION);
        parameters.put("oaId", oaId());
        if (identity != null && !isBlank(identity.dctx)) {
            parameters.put("dctx", identity.dctx);
        }
        if (identity != null && !isBlank(identity.tzNonce) && !isBlank(identity.tzSign)) {
            parameters.put("tzNonce", identity.tzNonce);
            parameters.put("tzSign", identity.tzSign);
        }
    }

    private String packageVersionCode(String packageName) {
        if (context == null) return "0";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return Build.VERSION.SDK_INT >= 28
                    ? String.valueOf(info.getLongVersionCode()) : String.valueOf(info.versionCode);
        } catch (PackageManager.NameNotFoundException exception) {
            return "0";
        }
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
        return "2".equals(protocol) || "3".equals(protocol) ? protocol : "1";
    }

    private String hasGmsCore() {
        return String.valueOf("1".equals(systemProperty("ro.miui.has_gmscore")));
    }

    private String hybridFrameworkVersion() {
        if (context == null) {
            return "";
        }
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.miui.hybrid", 0);
            return Build.VERSION.SDK_INT >= 28
                    ? String.valueOf(packageInfo.getLongVersionCode())
                    : String.valueOf(packageInfo.versionCode);
        } catch (Exception exception) {
            return "";
        }
    }

    private String oaId() {
        String reflected = reflectedOaId();
        if (!isBlank(reflected)) {
            return reflected;
        }
        return "6f24320b1e9596bf";
    }

    private String reflectedOaId() {
        if (context == null) {
            return "";
        }
        try {
            Class<?> providerClass = Class.forName("com.android.id.impl.IdProviderImpl");
            Object provider = providerClass.getDeclaredConstructor().newInstance();
            Method method = providerClass.getMethod("getOAID", Context.class);
            Object result = method.invoke(provider, context);
            return result instanceof String ? (String) result : "";
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private String loadInstanceId(Context appContext) {
        if (appContext == null) {
            return UUID.randomUUID().toString();
        }
        android.content.SharedPreferences preferences = appContext.getSharedPreferences(
                SETTINGS_FILE, Context.MODE_PRIVATE);
        String stored = preferences.getString(INSTANCE_ID_KEY, "");
        if (!isBlank(stored)) {
            return stored;
        }
        String generated = UUID.randomUUID().toString();
        preferences.edit().putString(INSTANCE_ID_KEY, generated).apply();
        return generated;
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
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
        parameters.put("useExpId", deviceIdentity == null
                ? DEFAULT_EXPERIMENT_IDS
                : deviceIdentity.readExperimentIds(DEFAULT_EXPERIMENT_IDS));
    }

    private LinkedHashMap<String, String> searchParameters(String keyword, int page) {
        LinkedHashMap<String, String> parameters = baseParameters();
        removeOfficialOnlyParameters(parameters);
        parameters.put("bottomTab", "true");
        parameters.put("flag", "2");
        parameters.put("feReload", "false");
        parameters.put("isNewUI", "true");
        parameters.put("ref", "input");
        parameters.put("responseType", "1");
        parameters.put("searchFrom", "input");
        parameters.put("minacompatible", "1");
        parameters.put("native", "1");
        parameters.put("keyword", keyword);
        parameters.put("renderType", "1");
        parameters.put("pageRef", "com.miui.home");
        parameters.put("showGoogleAppsType", "2");
        parameters.put("sourcePackage", "com.miui.home");
        parameters.put("supportSlide", "1");
        parameters.put("previousFromRef", "searchSuggest");
        parameters.put("voiceAssistVersion", "507513001");
        parameters.put("suggestV", "1");
        parameters.put("isSupportCreative", "true");
        parameters.put("refs", "input-searchResult");
        parameters.put("search_session", instanceId + System.currentTimeMillis());
        parameters.put("page", String.valueOf(page));
        return parameters;
    }

    private void removeOfficialOnlyParameters(Map<String, String> parameters) {
        parameters.remove("customization");
        parameters.remove("minaPlatformVersion");
        parameters.remove("recentSearchKey");
        parameters.remove("gameCenterVersionCode");
        parameters.remove("session_id");
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

    private String articleBannerImage(JSONObject block) {
        JSONObject bannerInfo = block.optJSONObject("bannerInfo");
        String image = firstUsableImage(
                bannerInfo, "banner", "mticon", "webViewPic", "imgUrl");
        if (image.isEmpty()) {
            image = firstUsableImage(
                    block, "banner", "mticon", "webViewPic", "imgUrl");
        }
        return image;
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
        try {
            return new JSONObject(KtorMarketHttpClient.get(url, getHeaders()));
        } catch (JSONException exception) {
            throw new IOException("Xiaomi API returned invalid JSON", exception);
        }
    }

    private JSONObject postJson(String url, java.util.Map<String, String> parameters)
            throws IOException {
        try {
            return new JSONObject(KtorMarketHttpClient.postForm(
                    url, parameters, postHeaders()));
        } catch (JSONException exception) {
            throw new IOException("Xiaomi API returned invalid JSON", exception);
        }
    }

    private java.util.Map<String, String> getHeaders() {
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        LinkedHashMap<String, String> parameters = baseParameters();
        headers.put("User-Agent", userAgent(parameters));
        headers.put("x-version-name", MARKET_VERSION_NAME);
        headers.put("x-version-code", MARKET_VERSION_CODE);
        return headers;
    }

    private java.util.Map<String, String> postHeaders() {
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        LinkedHashMap<String, String> parameters = baseParameters();
        headers.put("User-Agent", userAgent(parameters));
        return headers;
    }

    public java.util.Map<String, String> downloadHeaders() {
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>(getHeaders());
        headers.put("x-pkg-name", MARKET_PACKAGE);
        return java.util.Collections.unmodifiableMap(headers);
    }

    private String userAgent(LinkedHashMap<String, String> parameters) {
        String androidVersion = parameters.get("androidVersion");
        String model = parameters.get("model");
        String buildId = profileOverrides.get("buildId");
        if (isBlank(buildId)) {
            buildId = nonBlank(systemProperty("ro.build.id"), Build.ID);
        }
        return "Dalvik/2.1.0 (Linux; U; Android " + androidVersion + "; "
                + model + " Build/" + buildId + ")";
    }

    private JSONArray findSearchList(JSONObject response) {
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

    private List<MarketAppInfo> parseSearchApps(JSONArray list) {
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

    private List<UpdateInfo> parseUpdates(
            JSONObject response,
            LinkedHashMap<String, InstalledPackageInfo> installedByPackage) throws IOException {
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
        if (matching != null) return matching;
        JSONObject openEnded = highestOpenEndedApk(apks);
        return openEnded == null ? highestApk(apks, installedVersionCode, false) : openEnded;
    }

    private JSONObject highestOpenEndedApk(JSONArray apks) {
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

    private List<MarketAppInfo> extractSearchApps(JSONObject item) {
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

    private MarketAppInfo tryParseApp(JSONObject json) {
        if (json == null || json.optString("packageName", "").isEmpty()) {
            return null;
        }
        try {
            return parseApp(json);
        } catch (IOException exception) {
            Log.w(TAG, "skip invalid search app", exception);
            return null;
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
                .quickApp(isQuickApp(json))
                .reservationApp(isReservationApp(json))
                .introduction(firstText(json, "briefShow", "introduction", "description"))
                .downloadCount(json.optLong("downloadCount", 0))
                .commentCount(XiaomiDetailParser.firstLong(json, "ratingTotalCount", "commentCount", "totalCommentCount", "commentNum"))
                .ageClassification(json.optString("ageClassification", ""))
                .updateTime(json.optLong("updateTime", 0))
                .registrationNumber(json.optString("registrationNum", ""))
                .screenshotUrls(XiaomiDetailParser.parseScreenshotUrls(json))
                .build();
    }

    private boolean isQuickApp(JSONObject json) {
        return json.optBoolean("isQuickApp", json.optBoolean("quickApp", false))
                || QUICK_GAME_TYPE.equals(json.optString("type", ""));
    }

    private boolean isReservationApp(JSONObject json) {
        boolean explicit = json.optBoolean(
                "isReservationApp", json.optBoolean("reservationApp", false));
        long versionCode = json.optLong("versionCode", 0);
        int subscribeState = json.optInt("subscribeState", 0);
        return explicit || (subscribeState > 0 && versionCode <= 0);
    }

    private DownloadMetadata parseDownload(MarketAppInfo app, JSONObject response) throws IOException {
        JSONObject data = response.optJSONObject("data");
        JSONObject source = response;
        JSONArray apks = response.optJSONArray("apks");
        if (apks == null && data != null) {
            source = data;
            apks = data.optJSONArray("apks");
        }
        if (apks == null) {
            String reason = firstText(source, "unfitnessDesc", "errorDesc", "message", "msg");
            throw new IOException(reason.isEmpty() ? "下载失败：无 APK 包体" : "下载失败：" + reason);
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

    private String downloadHost(JSONObject primary, JSONObject secondary) {
        JSONArray hosts = primary.optJSONArray("hosts");
        if (hosts == null) hosts = secondary.optJSONArray("hosts");
        String host = hosts == null ? "" : hosts.optString(0, "");
        return isBlank(host) ? DOWNLOAD_API : host;
    }

    private List<ApkArtifact> parseArtifacts(JSONArray apks, String host, int diffVersion)
            throws IOException {
        List<ApkArtifact> artifacts = new ArrayList<>();
        for (int index = 0; index < apks.length(); index++) {
            try {
                JSONObject item = apks.getJSONObject(index);
                String url = firstText(item, "url", "fullUrl");
                if (url.isEmpty()) {
                    throw new IOException("Xiaomi API artifact has no url at index " + index);
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

    private String normalizeDownload(String value, String host) {
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
