package com.hyper.market.api;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 网络请求、签名参数构建与设备信息工具层（从 XiaomiApiClient 拆分）。
 * 持有 context / deviceIdentity / profileOverrides / instanceId 等客户端状态，
 * 提供 baseParameters、签名请求、JSON 请求、请求头与设备参数工具。
 */
final class XiaomiApiSupport {
    static final String MARKET_API = "https://app.market.xiaomi.com/apm/";
    static final String UPDATE_API =
            "https://updateinfo.market.xiaomi.com/apm/updateinfo/v2?lo=CN";
    static final String DOWNLOAD_API = "https://fgb0.market.xiaomi.com/download/";
    static final String THUMBNAIL_BASE_URL =
            "https://sf0.market.xiaomi.com/thumbnail/";
    static final String MARKET_PACKAGE = "com.xiaomi.market";
    static final String MARKET_VERSION_NAME = "4.120.1";
    static final String MARKET_VERSION_CODE = "40007441";
    static final String MARKET_QUERY_VERSION = "40008341";
    static final String PAGE_CONFIG_VERSION = "18411801";
    static final String WEB_RES_VERSION = "3211";
    static final String DEFAULT_EXPERIMENT_IDS =
            "2023001,2311551,2398789,2398846,2346056,2075312,2362289,2378691,"
                    + "2403435,2263575,2404589,2333160,2286434,1411227,1978999,"
                    + "2368587,2362073,2059056";
    static final String DEFAULT_XMSF_VERSION = "70005022";
    private static final String INSTANCE_ID_KEY = "market_instance_id";
    private static final String SETTINGS_FILE = "market_settings";
    private static final String TAG = "XiaomiApi";

    private final Context context;
    private volatile Map<String, String> profileOverrides = Collections.emptyMap();
    private final String instanceId;
    private final XiaomiDeviceIdentity deviceIdentity;

    XiaomiApiSupport(Context context, XiaomiDeviceIdentity deviceIdentity) {
        this.context = context == null ? null : context.getApplicationContext();
        this.deviceIdentity = deviceIdentity;
        this.instanceId = loadInstanceId(this.context);
    }

    Context context() {
        return context;
    }

    String instanceId() {
        return instanceId;
    }

    XiaomiDeviceIdentity deviceIdentity() {
        return deviceIdentity;
    }

    Map<String, String> profileOverrides() {
        return profileOverrides;
    }

    void setProfile(String source, Map<String, String> overrides) {
        if ("preset".equals(source) || "device".equals(source)) {
            profileOverrides = Collections.emptyMap();
            return;
        }
        profileOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    LinkedHashMap<String, String> baseParameters() {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        addClientParameters(parameters);
        addFeatureParameters(parameters);
        parameters.putAll(profileOverrides);
        parameters.remove("buildId");
        return parameters;
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

    static String systemProperty(String key) {
        try {
            Method get = Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("get", String.class);
            return String.valueOf(get.invoke(null, key));
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    static String nonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    static boolean isBlank(String value) {
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

    void removeOfficialOnlyParameters(Map<String, String> parameters) {
        parameters.remove("customization");
        parameters.remove("minaPlatformVersion");
        parameters.remove("recentSearchKey");
        parameters.remove("gameCenterVersionCode");
        parameters.remove("session_id");
    }

    String signedGet(String url, Map<String, String> parameters) {
        return XiaomiApiSigner.signedGet(url, parameters);
    }

    XiaomiApiSigner.SignedPostRequest signedPost(String url, Map<String, String> parameters) {
        return XiaomiApiSigner.signedPost(url, parameters);
    }

    JSONObject getJson(String url) throws java.io.IOException {
        try {
            return new JSONObject(KtorMarketHttpClient.get(url, getHeaders()));
        } catch (JSONException exception) {
            throw new java.io.IOException("Xiaomi API returned invalid JSON", exception);
        }
    }

    JSONObject postJson(String url, Map<String, String> parameters) throws java.io.IOException {
        try {
            return new JSONObject(KtorMarketHttpClient.postForm(url, parameters, postHeaders()));
        } catch (JSONException exception) {
            throw new java.io.IOException("Xiaomi API returned invalid JSON", exception);
        }
    }

    Map<String, String> getHeaders() {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        LinkedHashMap<String, String> parameters = baseParameters();
        headers.put("User-Agent", userAgent(parameters));
        headers.put("x-version-name", MARKET_VERSION_NAME);
        headers.put("x-version-code", MARKET_VERSION_CODE);
        return headers;
    }

    private Map<String, String> postHeaders() {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        LinkedHashMap<String, String> parameters = baseParameters();
        headers.put("User-Agent", userAgent(parameters));
        return headers;
    }

    Map<String, String> downloadHeaders() {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(getHeaders());
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
}
