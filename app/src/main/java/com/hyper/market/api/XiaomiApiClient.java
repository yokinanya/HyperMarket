package com.hyper.market.api;

import com.hyper.market.model.DownloadMetadata;
import com.hyper.market.model.InstalledPackageInfo;
import com.hyper.market.model.MarketAppDetails;
import com.hyper.market.model.MarketAppInfo;
import com.hyper.market.model.SearchFeedPage;
import com.hyper.market.model.TodayArticle;
import com.hyper.market.model.TodayFeedPage;
import com.hyper.market.model.UpdateInfo;

import android.os.Build;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import android.util.Log;

public final class XiaomiApiClient {
    private static final String MARKET_API = "https://app.market.xiaomi.com/apm/";
    private static final String UPDATE_API =
            "https://updateinfo.market.xiaomi.com/apm/updateinfo/v2?lo=CN";
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
    private final Context context;
    private final String instanceId;
    private final XiaomiDeviceIdentity deviceIdentity;
    private final XiaomiApiSupport support;
    private final Object identityBootstrapLock = new Object();
    private final Map<String, String> oldApkHashCache = new ConcurrentHashMap<>();

    public XiaomiApiClient() {
        this(null);
    }

    public XiaomiApiClient(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        this.deviceIdentity = this.context == null ? null : new XiaomiDeviceIdentity(this.context);
        this.support = new XiaomiApiSupport(this.context, this.deviceIdentity);
        this.instanceId = this.support.instanceId();
    }

    public void setProfileOverrides(java.util.Map<String, String> overrides) {
        support.setProfile("custom", overrides);
    }

    public void setProfile(String source, java.util.Map<String, String> overrides) {
        support.setProfile(source, overrides);
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
        JSONArray list = XiaomiResponseParsers.findSearchList(response);
        List<MarketAppInfo> apps = list == null
                ? Collections.emptyList() : XiaomiResponseParsers.parseSearchApps(list);
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
        List<UpdateInfo> updates = XiaomiResponseParsers.parseUpdates(response, installedByPackage);
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
        return XiaomiResponseParsers.parseDiffSizes(
                postJson(request.getUrl(), request.getParameters()));
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
            throw XiaomiResponseParsers.combinedTodayFailure(feedFailure, goldMiFailure);
        }
        return XiaomiResponseParsers.interleaveTodayFeeds(
                feed == null ? XiaomiResponseParsers.emptyTodayPage() : feed,
                goldMi == null ? XiaomiResponseParsers.emptyTodayPage() : goldMi);
    }

    private TodayFeedPage loadTodayFeed(int page) throws IOException {
        LinkedHashMap<String, String> parameters = todayParameters();
        parameters.put("page", String.valueOf(page));
        String url = signedGet(MARKET_API + "today?", parameters);
        return XiaomiResponseParsers.parseTodayFeed(getJson(url));
    }

    private TodayFeedPage loadGoldMiFeed(int page) throws IOException {
        LinkedHashMap<String, String> parameters = todayParameters();
        parameters.put("page", String.valueOf(page));
        parameters.put("pageSize", String.valueOf(GOLD_MI_PAGE_SIZE));
        parameters.put("ref", "goldmi");
        parameters.put("previousFromRef", "goldmi");
        String url = signedGet(MARKET_API + "zone/goldMiV2?", parameters);
        return XiaomiResponseParsers.parseGoldMiFeed(getJson(url));
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
        return XiaomiResponseParsers.parseTodayArticle(resourceId, getJson(url));
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
        return XiaomiDetailParser.parseDetails(response,
                XiaomiResponseParsers.parseApp(XiaomiResponseParsers.findObject(response)));
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
        return XiaomiResponseParsers.parseDownload(app, getJson(url));
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
        return support.baseParameters();
    }

    private String signedGet(String url, Map<String, String> parameters) {
        return support.signedGet(url, parameters);
    }

    private XiaomiApiSigner.SignedPostRequest signedPost(
            String url, Map<String, String> parameters) {
        return support.signedPost(url, parameters);
    }

    // moved to XiaomiApiSupport: addClientParameters

    // moved to XiaomiApiSupport: packageVersionCode

    // moved to XiaomiApiSupport: orderedResolution

    // moved to XiaomiApiSupport: supportedArchitectures

    // moved to XiaomiApiSupport: supportedIslandVersion

    // moved to XiaomiApiSupport: hasGmsCore

    // moved to XiaomiApiSupport: hybridFrameworkVersion

    // moved to XiaomiApiSupport: oaId

    // moved to XiaomiApiSupport: reflectedOaId

    // moved to XiaomiApiSupport: loadInstanceId

    // moved to XiaomiApiSupport: systemProperty

    private String nonBlank(String value, String fallback) {
        return XiaomiApiSupport.nonBlank(value, fallback);
    }

    private boolean isBlank(String value) {
        return XiaomiApiSupport.isBlank(value);
    }

    // moved to XiaomiApiSupport: addFeatureParameters

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
        support.removeOfficialOnlyParameters(parameters);
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
        return support.getJson(url);
    }

    private JSONObject postJson(String url, java.util.Map<String, String> parameters)
            throws IOException {
        return support.postJson(url, parameters);
    }

    public java.util.Map<String, String> downloadHeaders() {
        return support.downloadHeaders();
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

}
